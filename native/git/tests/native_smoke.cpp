#include "nexora/git_core.h"

#include <git2.h>

#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>

namespace fs = std::filesystem;

namespace {

const nexora::git::Author kAuthor{
    "Nexora Test",
    "nexora@example.invalid",
};

void require(bool condition, const std::string& message) {
    if (!condition) {
        throw std::runtime_error(message);
    }
}

void raw_check(int code, const std::string& operation) {
    if (code >= 0) return;

    const git_error* error = git_error_last();
    const std::string detail =
        error != nullptr && error->message != nullptr
            ? error->message
            : "unknown libgit2 error";
    throw std::runtime_error(operation + ": " + detail);
}

std::string unique_name() {
    const auto now = std::chrono::steady_clock::now()
        .time_since_epoch()
        .count();
    return "nexoragit-native-" + std::to_string(now);
}

void write_file(
    const fs::path& path,
    const std::string& value
) {
    std::ofstream output(path, std::ios::trunc);
    output << value;
}

void append_file(
    const fs::path& path,
    const std::string& value
) {
    std::ofstream output(path, std::ios::app);
    output << value;
}

void create_bare_remote(const fs::path& path) {
    git_repository* repository = nullptr;
    raw_check(
        git_repository_init(
            &repository,
            path.string().c_str(),
            1
        ),
        "Initialize bare remote"
    );
    raw_check(
        git_repository_set_head(
            repository,
            "refs/heads/main"
        ),
        "Set bare remote HEAD"
    );
    git_repository_free(repository);
}

void add_remote(
    const fs::path& repository_path,
    const std::string& name,
    const fs::path& remote_path
) {
    git_repository* repository = nullptr;
    raw_check(
        git_repository_open(
            &repository,
            repository_path.string().c_str()
        ),
        "Open repository for remote creation"
    );

    git_remote* remote = nullptr;
    const int rc = git_remote_create(
        &remote,
        repository,
        name.c_str(),
        remote_path.string().c_str()
    );

    if (remote != nullptr) git_remote_free(remote);
    git_repository_free(repository);
    raw_check(rc, "Create remote");
}

void basic_local_workflow(const fs::path& repository) {
    const std::string initialized =
        nexora::git::init_repository(repository.string());
    require(
        initialized.find("\"head\":\"main\"") != std::string::npos,
        "Repository did not initialize on main"
    );

    write_file(repository / "README.md", "# Native Git test\n");

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

    const std::string staged =
        nexora::git::diff(repository.string(), "staged");
    require(
        staged.find("Native Git test") != std::string::npos,
        "Staged diff did not contain staged content"
    );

    const std::string first_commit =
        nexora::git::commit(
            repository.string(),
            "Initial commit",
            kAuthor
        );
    require(
        first_commit.find("\"oid\":\"") != std::string::npos,
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

    write_file(
        repository / "feature.txt",
        "feature branch\n"
    );
    nexora::git::stage(
        repository.string(),
        {"feature.txt"}
    );
    nexora::git::commit(
        repository.string(),
        "Feature commit",
        kAuthor
    );

    nexora::git::checkout(
        repository.string(),
        "main"
    );

    const std::string merge_result =
        nexora::git::merge(
            repository.string(),
            "feature/native",
            kAuthor
        );
    require(
        merge_result.find("\"state\":\"fast_forward\"") !=
            std::string::npos,
        "Expected local fast-forward merge"
    );

    append_file(repository / "README.md", "modified\n");

    write_file(
        repository / "unrelated.txt",
        "not part of readme diff\n"
    );

    const std::string patch =
        nexora::git::diff(repository.string(), "unstaged");
    require(
        patch.find("modified") != std::string::npos,
        "Working tree diff did not contain modification"
    );

    const std::string readme_patch =
        nexora::git::diff(
            repository.string(),
            "unstaged",
            "README.md"
        );
    require(
        readme_patch.find("modified") != std::string::npos,
        "Path-scoped diff did not contain README change"
    );
    require(
        readme_patch.find("unrelated.txt") ==
            std::string::npos,
        "Path-scoped diff leaked unrelated file"
    );

    write_file(
        repository / "literal[1].txt",
        "bracket base\n"
    );
    write_file(
        repository / "literal1.txt",
        "plain base\n"
    );
    nexora::git::stage(
        repository.string(),
        {"literal[1].txt", "literal1.txt"}
    );
    nexora::git::commit(
        repository.string(),
        "Add literal path fixtures",
        kAuthor
    );

    append_file(
        repository / "literal[1].txt",
        "bracket change\n"
    );
    append_file(
        repository / "literal1.txt",
        "plain change\n"
    );

    const std::string literal_patch =
        nexora::git::diff(
            repository.string(),
            "unstaged",
            "literal[1].txt"
        );
    require(
        literal_patch.find("literal[1].txt") !=
            std::string::npos,
        "Literal path diff did not contain target file"
    );
    require(
        literal_patch.find("literal1.txt") ==
            std::string::npos,
        "Literal path diff treated path as wildcard"
    );

    write_file(
        repository / "literal[1].txt",
        "bracket base\n"
    );
    write_file(
        repository / "literal1.txt",
        "plain base\n"
    );

    nexora::git::stage(
        repository.string(),
        {"README.md"}
    );
    nexora::git::unstage(
        repository.string(),
        {"README.md"}
    );

    const std::string after_unstage =
        nexora::git::status(repository.string());
    require(
        after_unstage.find("\"workingTree\":true") !=
            std::string::npos,
        "Unstage did not preserve working tree modification"
    );

    nexora::git::stage(
        repository.string(),
        {"README.md"}
    );
    nexora::git::commit(
        repository.string(),
        "Prepare remote workflow",
        kAuthor
    );

    const std::string branches =
        nexora::git::branches(repository.string());
    require(
        branches.find("feature/native") != std::string::npos,
        "Feature branch missing"
    );
    require(
        branches.find("\"name\":\"main\"") != std::string::npos,
        "Main branch missing"
    );

    require(
        nexora::git::conflicts(repository.string()) == "[]",
        "Unexpected conflicts in local workflow"
    );

    const std::string readme_history =
        nexora::git::history(
            repository.string(),
            "README.md",
            20
        );
    require(
        readme_history.find("Prepare remote workflow") !=
            std::string::npos,
        "File history did not include README update"
    );
    require(
        readme_history.find("Initial commit") !=
            std::string::npos,
        "File history did not include initial README commit"
    );
    require(
        readme_history.find("Feature commit") ==
            std::string::npos,
        "File history included an unrelated commit"
    );

    const std::string readme_blame =
        nexora::git::blame(
            repository.string(),
            "README.md"
        );
    require(
        readme_blame.find("\"authorName\":\"Nexora Test\"") !=
            std::string::npos,
        "Blame did not expose commit author"
    );
    require(
        readme_blame.find("\"originalPath\":\"README.md\"") !=
            std::string::npos,
        "Blame did not expose README path"
    );
}

void remote_workflow(
    const fs::path& seed,
    const fs::path& remote,
    const fs::path& clone_a,
    const fs::path& clone_b
) {
    create_bare_remote(remote);
    add_remote(seed, "origin", remote);

    const std::string clean_remote =
        nexora::git::remote_url(seed.string(), "origin");
    require(
        clean_remote == remote.string(),
        "Remote URL inspection returned unexpected value"
    );

    nexora::git::push(
        seed.string(),
        "origin",
        "refs/heads/main:refs/heads/main",
        {}
    );

    const std::string cloned_a =
        nexora::git::clone_repository(
            remote.string(),
            clone_a.string(),
            {}
        );
    const std::string cloned_b =
        nexora::git::clone_repository(
            remote.string(),
            clone_b.string(),
            {}
        );

    require(
        cloned_a.find("\"head\":\"main\"") != std::string::npos &&
            cloned_b.find("\"head\":\"main\"") != std::string::npos,
        "Clones did not check out main"
    );

    append_file(
        clone_a / "README.md",
        "remote change\n"
    );
    nexora::git::stage(
        clone_a.string(),
        {"README.md"}
    );
    nexora::git::commit(
        clone_a.string(),
        "Remote change",
        kAuthor
    );
    nexora::git::push(
        clone_a.string(),
        "origin",
        "",
        {}
    );

    nexora::git::fetch(
        clone_b.string(),
        "origin",
        {}
    );

    const std::string remote_branches =
        nexora::git::branches(clone_b.string());
    require(
        remote_branches.find("origin/main") != std::string::npos,
        "Fetch did not update origin/main"
    );

    const std::string first_pull =
        nexora::git::pull(
            clone_b.string(),
            "origin",
            kAuthor,
            {}
        );
    require(
        first_pull.find("\"state\":\"fast_forward\"") !=
            std::string::npos ||
        first_pull.find("\"state\":\"up_to_date\"") !=
            std::string::npos,
        "Pull did not fast-forward clone B"
    );

    write_file(
        clone_a / "conflict.txt",
        "base\n"
    );
    nexora::git::stage(
        clone_a.string(),
        {"conflict.txt"}
    );
    nexora::git::commit(
        clone_a.string(),
        "Add conflict base",
        kAuthor
    );
    nexora::git::push(
        clone_a.string(),
        "origin",
        "",
        {}
    );

    nexora::git::pull(
        clone_b.string(),
        "origin",
        kAuthor,
        {}
    );

    write_file(
        clone_b / "conflict.txt",
        "from clone B\n"
    );
    nexora::git::stage(
        clone_b.string(),
        {"conflict.txt"}
    );
    nexora::git::commit(
        clone_b.string(),
        "Clone B divergence",
        kAuthor
    );

    write_file(
        clone_a / "conflict.txt",
        "from clone A\n"
    );
    nexora::git::stage(
        clone_a.string(),
        {"conflict.txt"}
    );
    nexora::git::commit(
        clone_a.string(),
        "Clone A divergence",
        kAuthor
    );
    nexora::git::push(
        clone_a.string(),
        "origin",
        "",
        {}
    );

    const std::string conflict_pull =
        nexora::git::pull(
            clone_b.string(),
            "origin",
            kAuthor,
            {}
        );

    require(
        conflict_pull.find("\"state\":\"conflicts\"") !=
            std::string::npos,
        "Divergent pull did not produce a conflict"
    );

    const std::string conflicts =
        nexora::git::conflicts(clone_b.string());
    require(
        conflicts.find("conflict.txt") != std::string::npos,
        "Conflict list did not contain conflict.txt"
    );
}

}  // namespace

int main() {
    const fs::path root =
        fs::temp_directory_path() / unique_name();
    const fs::path seed = root / "seed";
    const fs::path remote = root / "remote.git";
    const fs::path clone_a = root / "clone-a";
    const fs::path clone_b = root / "clone-b";

    try {
        fs::create_directories(seed);

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

        basic_local_workflow(seed);
        remote_workflow(
            seed,
            remote,
            clone_a,
            clone_b
        );

        fs::remove_all(root);
        std::cout
            << "Nexora Git native workflow tests passed\n";
        return 0;
    } catch (const std::exception& error) {
        fs::remove_all(root);
        std::cerr << error.what() << "\n";
        return 1;
    }
}
