#include "nexora/git_core.h"

#include <git2.h>
#include <git2/deprecated.h>
#include <git2/sys/filter.h>
#include <mbedtls/sha256.h>

#include <filesystem>
#include <fstream>
#include <iomanip>
#include <sstream>
#include <string>
#include <vector>

namespace nexora::git {
namespace {

namespace fs = std::filesystem;

constexpr const char* kLfsVersion =
    "version https://git-lfs.github.com/spec/v1";

struct LfsPointer {
    std::string oid;
    uint64_t size = 0;
};

bool parse_lfs_pointer(
    const char* data,
    size_t size,
    LfsPointer* out
) {
    if (data == nullptr || out == nullptr ||
        size == 0 || size > 4096) {
        return false;
    }

    const std::string text(data, size);
    std::istringstream input(text);
    std::string version;
    std::string oid_line;
    std::string size_line;

    if (!std::getline(input, version) ||
        version != kLfsVersion ||
        !std::getline(input, oid_line) ||
        !std::getline(input, size_line)) {
        return false;
    }

    constexpr const char* kOidPrefix = "oid sha256:";
    constexpr const char* kSizePrefix = "size ";

    if (oid_line.rfind(kOidPrefix, 0) != 0 ||
        size_line.rfind(kSizePrefix, 0) != 0) {
        return false;
    }

    const std::string oid =
        oid_line.substr(std::char_traits<char>::length(kOidPrefix));
    if (oid.size() != 64) return false;

    for (const char ch : oid) {
        const bool digit = ch >= '0' && ch <= '9';
        const bool lower = ch >= 'a' && ch <= 'f';
        const bool upper = ch >= 'A' && ch <= 'F';
        if (!digit && !lower && !upper) return false;
    }

    uint64_t parsed_size = 0;
    try {
        size_t consumed = 0;
        parsed_size = std::stoull(
            size_line.substr(
                std::char_traits<char>::length(kSizePrefix)
            ),
            &consumed
        );
        if (consumed == 0) return false;
    } catch (...) {
        return false;
    }

    out->oid = oid;
    out->size = parsed_size;
    return true;
}

std::string sha256_hex(
    const char* data,
    size_t size
) {
    unsigned char digest[32]{};
    const int rc = mbedtls_sha256(
        reinterpret_cast<const unsigned char*>(data),
        size,
        digest,
        0
    );
    if (rc != 0) {
        throw GitError(
            GIT_ERROR,
            0,
            "Git LFS SHA-256 calculation failed"
        );
    }

    std::ostringstream out;
    out << std::hex << std::setfill('0');
    for (const unsigned char byte : digest) {
        out << std::setw(2)
            << static_cast<unsigned int>(byte);
    }
    return out.str();
}

fs::path lfs_object_path(
    git_repository* repository,
    const std::string& oid
) {
    const char* git_dir = git_repository_path(repository);
    if (git_dir == nullptr || oid.size() != 64) {
        return {};
    }

    return fs::path(git_dir) /
        "lfs" /
        "objects" /
        oid.substr(0, 2) /
        oid.substr(2, 2) /
        oid;
}

bool write_lfs_object(
    git_repository* repository,
    const std::string& oid,
    const char* data,
    size_t size
) {
    const fs::path target =
        lfs_object_path(repository, oid);
    if (target.empty()) return false;

    std::error_code error;
    fs::create_directories(
        target.parent_path(),
        error
    );
    if (error) return false;

    if (fs::exists(target, error) &&
        !error &&
        fs::file_size(target, error) == size &&
        !error) {
        return true;
    }

    const fs::path temporary =
        target.string() + ".nexora.tmp";
    {
        std::ofstream output(
            temporary,
            std::ios::binary | std::ios::trunc
        );
        if (!output) return false;
        output.write(
            data,
            static_cast<std::streamsize>(size)
        );
        if (!output.good()) return false;
    }

    fs::rename(temporary, target, error);
    if (error) {
        fs::remove(temporary);
        return false;
    }
    return true;
}

bool read_lfs_object(
    git_repository* repository,
    const LfsPointer& pointer,
    std::vector<char>* bytes
) {
    if (bytes == nullptr) return false;
    const fs::path source =
        lfs_object_path(repository, pointer.oid);
    if (source.empty()) return false;

    std::error_code error;
    if (!fs::is_regular_file(source, error) || error) {
        return false;
    }

    const uint64_t actual_size =
        fs::file_size(source, error);
    if (error || actual_size != pointer.size) {
        return false;
    }

    std::ifstream input(source, std::ios::binary);
    if (!input) return false;

    bytes->assign(
        std::istreambuf_iterator<char>(input),
        std::istreambuf_iterator<char>()
    );
    return input.good() || input.eof();
}

std::string pointer_text(
    const std::string& oid,
    size_t size
) {
    return std::string(kLfsVersion) +
        "\n" +
        "oid sha256:" + oid +
        "\nsize " +
        std::to_string(size) +
        "\n";
}

int lfs_filter_apply(
    git_filter*,
    void**,
    git_buf* output,
    const git_buf* input,
    const git_filter_source* source
) {
    if (output == nullptr ||
        input == nullptr ||
        source == nullptr ||
        input->ptr == nullptr) {
        return GIT_PASSTHROUGH;
    }

    git_repository* repository =
        git_filter_source_repo(source);
    if (repository == nullptr) {
        return GIT_PASSTHROUGH;
    }

    const git_filter_mode_t mode =
        git_filter_source_mode(source);

    try {
        if (mode == GIT_FILTER_TO_ODB) {
            LfsPointer existing{};
            if (parse_lfs_pointer(
                    input->ptr,
                    input->size,
                    &existing
                )) {
                return git_buf_set(
                    output,
                    input->ptr,
                    input->size
                );
            }

            const std::string oid =
                sha256_hex(
                    input->ptr,
                    input->size
                );
            if (!write_lfs_object(
                    repository,
                    oid,
                    input->ptr,
                    input->size
                )) {
                giterr_set_str(
                    GIT_ERROR_FILTER,
                    "Nexora Git could not store the LFS object locally"
                );
                return GIT_ERROR;
            }

            const std::string pointer =
                pointer_text(oid, input->size);
            return git_buf_set(
                output,
                pointer.data(),
                pointer.size()
            );
        }

        if (mode == GIT_FILTER_TO_WORKTREE) {
            LfsPointer pointer{};
            if (!parse_lfs_pointer(
                    input->ptr,
                    input->size,
                    &pointer
                )) {
                return GIT_PASSTHROUGH;
            }

            std::vector<char> bytes;
            if (!read_lfs_object(
                    repository,
                    pointer,
                    &bytes
                )) {
                return git_buf_set(
                    output,
                    input->ptr,
                    input->size
                );
            }

            return git_buf_set(
                output,
                bytes.data(),
                bytes.size()
            );
        }

        return GIT_PASSTHROUGH;
    } catch (const std::exception& error) {
        giterr_set_str(
            GIT_ERROR_FILTER,
            error.what()
        );
        return GIT_ERROR;
    }
}

git_filter nexora_lfs_filter = GIT_FILTER_INIT;

}  // namespace

void register_lfs_filter() {
    if (git_filter_lookup("lfs") != nullptr) {
        return;
    }

    nexora_lfs_filter.attributes = "filter=lfs";
    nexora_lfs_filter.apply = lfs_filter_apply;

    const int rc = git_filter_register(
        "lfs",
        &nexora_lfs_filter,
        GIT_FILTER_DRIVER_PRIORITY
    );

    if (rc < 0 && rc != GIT_EEXISTS) {
        const git_error* error = git_error_last();
        throw GitError(
            rc,
            error != nullptr ? error->klass : 0,
            std::string("Register Git LFS filter: ") +
                (
                    error != nullptr &&
                    error->message != nullptr
                        ? error->message
                        : "unknown libgit2 error"
                )
        );
    }
}

}  // namespace nexora::git
