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

std::string json_string(
    const std::string& payload,
    const std::string& key
) {
    const std::string prefix = "\"" + key + "\":\"";
    const size_t start = payload.find(prefix);
    if (start == std::string::npos) return "";
    const size_t value_start = start + prefix.size();
    const size_t end = payload.find('"', value_start);
    if (end == std::string::npos) return "";
    return payload.substr(value_start, end - value_start);
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

    const std::string listed_remotes =
        nexora::git::remotes(clone_b.string());
    require(
        listed_remotes.find("\"name\":\"origin\"") !=
            std::string::npos,
        "Origin remote was not listed"
    );

    nexora::git::add_remote(
        clone_b.string(),
        "backup",
        remote.string()
    );
    nexora::git::rename_remote(
        clone_b.string(),
        "backup",
        "mirror"
    );
    require(
        nexora::git::remotes(clone_b.string())
            .find("\"name\":\"mirror\"") !=
            std::string::npos,
        "Remote rename was not persisted"
    );
    nexora::git::remove_remote(
        clone_b.string(),
        "mirror"
    );
    require(
        nexora::git::remotes(clone_b.string())
            .find("\"name\":\"mirror\"") ==
            std::string::npos,
        "Remote delete was not persisted"
    );

    nexora::git::set_upstream(
        clone_b.string(),
        "main",
        "origin/main"
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

    const std::string divergence_before_pull =
        nexora::git::divergence(
            clone_b.string(),
            "main",
            "origin/main"
        );
    require(
        divergence_before_pull.find("\"ahead\":0") !=
            std::string::npos &&
        divergence_before_pull.find("\"behind\":1") !=
            std::string::npos,
        "Ahead/behind did not report clone B behind origin"
    );

    const std::string first_pull =
        nexora::git::pull_with_strategy(
            clone_b.string(),
            "origin",
            "ff_only",
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
        clone_b / "local-rebase.txt",
        "local rebase work\n"
    );
    nexora::git::stage(
        clone_b.string(),
        {"local-rebase.txt"}
    );
    nexora::git::commit(
        clone_b.string(),
        "Local rebase commit",
        kAuthor
    );

    write_file(
        clone_a / "remote-rebase.txt",
        "remote rebase work\n"
    );
    nexora::git::stage(
        clone_a.string(),
        {"remote-rebase.txt"}
    );
    nexora::git::commit(
        clone_a.string(),
        "Remote rebase commit",
        kAuthor
    );
    nexora::git::push(
        clone_a.string(),
        "origin",
        "",
        {}
    );

    const std::string rebase_pull =
        nexora::git::pull_with_strategy(
            clone_b.string(),
            "origin",
            "rebase",
            kAuthor,
            {}
        );
    require(
        rebase_pull.find("\"state\":\"rebased\"") !=
            std::string::npos,
        "Rebase pull did not replay the local commit"
    );
    require(
        nexora::git::repository_state(clone_b.string()) ==
            "none",
        "Repository remained in rebase state after clean rebase"
    );

    nexora::git::push(
        clone_b.string(),
        "origin",
        "",
        {}
    );
    nexora::git::pull(
        clone_a.string(),
        "origin",
        kAuthor,
        {}
    );

    nexora::git::create_branch(
        clone_b.string(),
        "lease-test",
        ""
    );
    nexora::git::checkout(
        clone_b.string(),
        "lease-test"
    );
    write_file(
        clone_b / "lease.txt",
        "lease base\n"
    );
    nexora::git::stage(
        clone_b.string(),
        {"lease.txt"}
    );
    nexora::git::commit(
        clone_b.string(),
        "Lease branch base",
        kAuthor
    );
    nexora::git::push(
        clone_b.string(),
        "origin",
        "refs/heads/lease-test:refs/heads/lease-test",
        {}
    );
    nexora::git::fetch(
        clone_b.string(),
        "origin",
        {}
    );

    const std::string lease_state =
        nexora::git::divergence(
            clone_b.string(),
            "lease-test",
            "origin/lease-test"
        );
    const std::string lease_oid =
        json_string(lease_state, "upstreamOid");
    require(
        !lease_oid.empty(),
        "Lease divergence did not expose upstream OID"
    );

    append_file(
        clone_b / "lease.txt",
        "lease rewrite\n"
    );
    nexora::git::stage(
        clone_b.string(),
        {"lease.txt"}
    );
    nexora::git::commit(
        clone_b.string(),
        "Lease update",
        kAuthor
    );
    const std::string lease_push =
        nexora::git::push_force_with_lease(
            clone_b.string(),
            "origin",
            "refs/heads/lease-test:refs/heads/lease-test",
            lease_oid,
            {}
        );
    require(
        lease_push.find("\"forceWithLease\":true") !=
            std::string::npos,
        "Force-with-lease did not report guarded push"
    );

    bool stale_lease_rejected = false;
    append_file(
        clone_b / "lease.txt",
        "stale lease attempt\n"
    );
    nexora::git::stage(
        clone_b.string(),
        {"lease.txt"}
    );
    nexora::git::commit(
        clone_b.string(),
        "Stale lease commit",
        kAuthor
    );
    try {
        nexora::git::push_force_with_lease(
            clone_b.string(),
            "origin",
            "refs/heads/lease-test:refs/heads/lease-test",
            lease_oid,
            {}
        );
    } catch (const nexora::git::GitError&) {
        stale_lease_rejected = true;
    }
    require(
        stale_lease_rejected,
        "Stale force-with-lease was not rejected"
    );

    nexora::git::checkout(
        clone_b.string(),
        "main"
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
    require(
        nexora::git::repository_state(clone_b.string()) ==
            "merge",
        "Repository did not retain merge state"
    );

    nexora::git::resolve_conflict(
        clone_b.string(),
        "conflict.txt",
        "ours"
    );
    require(
        nexora::git::conflicts(clone_b.string()) == "[]",
        "Use ours did not clear the merge conflict"
    );

    const std::string continued_merge =
        nexora::git::continue_merge(
            clone_b.string(),
            kAuthor
        );
    require(
        continued_merge.find("\"state\":\"merged\"") !=
            std::string::npos,
        "Continue merge did not create merge commit"
    );
    require(
        nexora::git::repository_state(clone_b.string()) ==
            "none",
        "Continue merge did not clear merge state"
    );
    require(
        nexora::git::conflicts(clone_b.string()) == "[]",
        "Conflicts remained after continue merge"
    );

    const std::string merge_history =
        nexora::git::history(
            clone_b.string(),
            "",
            1
        );
    require(
        merge_history.find("\"parentCount\":2") !=
            std::string::npos,
        "Resolved merge commit did not preserve two parents"
    );
}


void rebase_recovery_workflow(
    const fs::path& seed,
    const fs::path& remote,
    const fs::path& local_clone,
    const fs::path& peer_clone
) {
    fs::create_directories(seed);
    nexora::git::init_repository(seed.string());

    write_file(
        seed / "rebase-conflict.txt",
        "base\n"
    );
    write_file(
        seed / "abort-conflict.txt",
        "abort-base\n"
    );
    nexora::git::stage(
        seed.string(),
        {"rebase-conflict.txt", "abort-conflict.txt"}
    );
    nexora::git::commit(
        seed.string(),
        "Rebase recovery base",
        kAuthor
    );

    create_bare_remote(remote);
    add_remote(seed, "origin", remote);
    nexora::git::push(
        seed.string(),
        "origin",
        "refs/heads/main:refs/heads/main",
        {}
    );

    nexora::git::clone_repository(
        remote.string(),
        local_clone.string(),
        {}
    );
    nexora::git::clone_repository(
        remote.string(),
        peer_clone.string(),
        {}
    );

    nexora::git::set_upstream(
        local_clone.string(),
        "main",
        "origin/main"
    );
    nexora::git::set_upstream(
        peer_clone.string(),
        "main",
        "origin/main"
    );

    write_file(
        local_clone / "rebase-conflict.txt",
        "local version\n"
    );
    nexora::git::stage(
        local_clone.string(),
        {"rebase-conflict.txt"}
    );
    nexora::git::commit(
        local_clone.string(),
        "Local rebase conflict",
        kAuthor
    );

    write_file(
        peer_clone / "rebase-conflict.txt",
        "remote version\n"
    );
    nexora::git::stage(
        peer_clone.string(),
        {"rebase-conflict.txt"}
    );
    nexora::git::commit(
        peer_clone.string(),
        "Remote rebase conflict",
        kAuthor
    );
    nexora::git::push(
        peer_clone.string(),
        "origin",
        "",
        {}
    );

    const std::string conflict_result =
        nexora::git::pull_with_strategy(
            local_clone.string(),
            "origin",
            "rebase",
            kAuthor,
            {}
        );
    require(
        conflict_result.find("\"state\":\"conflicts\"") !=
            std::string::npos,
        "Rebase conflict was not surfaced"
    );
    require(
        nexora::git::repository_state(local_clone.string()) ==
            "rebase",
        "Repository did not retain rebase state"
    );

    nexora::git::resolve_conflict(
        local_clone.string(),
        "rebase-conflict.txt",
        "theirs"
    );
    require(
        nexora::git::conflicts(local_clone.string()) == "[]",
        "Use theirs did not clear the rebase conflict"
    );

    const std::string continued =
        nexora::git::continue_rebase(
            local_clone.string(),
            kAuthor
        );
    require(
        continued.find("\"state\":\"rebased\"") !=
            std::string::npos,
        "Continue rebase did not finish after resolution"
    );
    require(
        nexora::git::repository_state(local_clone.string()) ==
            "none",
        "Repository remained in rebase state after continue"
    );

    nexora::git::push(
        local_clone.string(),
        "origin",
        "",
        {}
    );
    nexora::git::pull(
        peer_clone.string(),
        "origin",
        kAuthor,
        {}
    );

    write_file(
        local_clone / "abort-conflict.txt",
        "local abort version\n"
    );
    nexora::git::stage(
        local_clone.string(),
        {"abort-conflict.txt"}
    );
    const std::string local_abort_commit =
        nexora::git::commit(
            local_clone.string(),
            "Local abort conflict",
            kAuthor
        );
    const std::string local_abort_oid =
        json_string(local_abort_commit, "oid");
    require(
        !local_abort_oid.empty(),
        "Local abort commit OID missing"
    );

    write_file(
        peer_clone / "abort-conflict.txt",
        "remote abort version\n"
    );
    nexora::git::stage(
        peer_clone.string(),
        {"abort-conflict.txt"}
    );
    nexora::git::commit(
        peer_clone.string(),
        "Remote abort conflict",
        kAuthor
    );
    nexora::git::push(
        peer_clone.string(),
        "origin",
        "",
        {}
    );

    const std::string abort_conflict =
        nexora::git::pull_with_strategy(
            local_clone.string(),
            "origin",
            "rebase",
            kAuthor,
            {}
        );
    require(
        abort_conflict.find("\"state\":\"conflicts\"") !=
            std::string::npos,
        "Abort scenario did not enter rebase conflict"
    );
    require(
        nexora::git::repository_state(local_clone.string()) ==
            "rebase",
        "Abort scenario did not retain rebase state"
    );

    nexora::git::abort_rebase(local_clone.string());

    require(
        nexora::git::repository_state(local_clone.string()) ==
            "none",
        "Abort rebase did not clear repository state"
    );

    const std::string after_abort_history =
        nexora::git::history(
            local_clone.string(),
            "",
            5
        );
    require(
        after_abort_history.find(local_abort_oid.substr(0, 7)) !=
            std::string::npos ||
        after_abort_history.find("Local abort conflict") !=
            std::string::npos,
        "Abort rebase did not restore the original local commit"
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
    const fs::path rebase_seed = root / "rebase-seed";
    const fs::path rebase_remote = root / "rebase-remote.git";
    const fs::path rebase_local = root / "rebase-local";
    const fs::path rebase_peer = root / "rebase-peer";

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
        rebase_recovery_workflow(
            rebase_seed,
            rebase_remote,
            rebase_local,
            rebase_peer
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
