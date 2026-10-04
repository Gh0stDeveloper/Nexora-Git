#include "nexora/git_core.h"

#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>

namespace fs = std::filesystem;

namespace {

void require(bool condition, const std::string& message) {
    if (!condition) {
        throw std::runtime_error(message);
    }
}

std::string unique_name() {
    const auto now = std::chrono::steady_clock::now()
        .time_since_epoch()
        .count();
    return "nexoragit-native-" + std::to_string(now);
}

}  // namespace

int main() {
    const fs::path root =
        fs::temp_directory_path() / unique_name();
    const fs::path repository = root / "repository";

    try {
        fs::create_directories(repository);

        nexora::git::initialize(
            root.string(),
            "/etc/ssl/certs"
        );

        const std::string libgit2_version =
            nexora::git::version();
        require(
            libgit2_version.rfind("1.9.", 0) == 0,
            "Unexpected libgit2 version: " + libgit2_version
        );

        const std::string initialized =
            nexora::git::init_repository(repository.string());
        require(
            initialized.find("\"head\":\"main\"") != std::string::npos,
            "Repository did not initialize on main"
        );

        {
            std::ofstream readme(repository / "README.md");
            readme << "# Native Git test\n";
        }

        const std::string untracked =
            nexora::git::status(repository.string());
        require(
            untracked.find("\"untracked\":true") != std::string::npos,
            "Untracked file was not detected"
        );

        nexora::git::stage(
            repository.string(),
            {"README.md"}
        );

        const std::string commit =
            nexora::git::commit(
                repository.string(),
                "Initial commit",
                {"Nexora Test", "nexora@example.invalid"}
            );
        require(
            commit.find("\"oid\":\"") != std::string::npos,
            "Commit OID was not returned"
        );

        nexora::git::create_branch(
            repository.string(),
            "feature/native",
            ""
        );
        nexora::git::checkout(
            repository.string(),
            "feature/native"
        );

        const std::string branches =
            nexora::git::branches(repository.string());
        require(
            branches.find("feature/native") != std::string::npos,
            "Feature branch missing"
        );
        require(
            branches.find("\"head\":true") != std::string::npos,
            "Checked-out branch not marked as HEAD"
        );

        {
            std::ofstream readme(
                repository / "README.md",
                std::ios::app
            );
            readme << "modified\n";
        }

        const std::string patch =
            nexora::git::diff(repository.string(), "all");
        require(
            patch.find("modified") != std::string::npos,
            "Working tree diff did not contain modification"
        );

        const std::string conflicts =
            nexora::git::conflicts(repository.string());
        require(
            conflicts == "[]",
            "Unexpected conflicts in smoke repository"
        );

        fs::remove_all(root);
        std::cout << "Nexora Git native smoke test passed\n";
        return 0;
    } catch (const std::exception& error) {
        fs::remove_all(root);
        std::cerr << error.what() << "\n";
        return 1;
    }
}
