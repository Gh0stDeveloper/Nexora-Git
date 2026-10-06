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

std::string index_blob_content(
    const fs::path& repository_path,
    const std::string& path
) {
    git_repository* repository = nullptr;
    raw_check(
        git_repository_open(
            &repository,
            repository_path.string().c_str()
        ),
        "Open repository for index blob"
    );

    git_index* index = nullptr;
    const int index_rc =
        git_repository_index(&index, repository);
    if (index_rc < 0) {
        git_repository_free(repository);
        raw_check(index_rc, "Open index for blob");
    }

    const git_index_entry* entry =
        git_index_get_bypath(index, path.c_str(), 0);
    if (entry == nullptr) {
        git_index_free(index);
        git_repository_free(repository);
        throw std::runtime_error(
            "Index entry not found: " + path
        );
    }

    git_blob* blob = nullptr;
    const int blob_rc =
        git_blob_lookup(&blob, repository, &entry->id);
    if (blob_rc < 0) {
        git_index_free(index);
        git_repository_free(repository);
        raw_check(blob_rc, "Open index blob");
    }

    const char* data = static_cast<const char*>(
        git_blob_rawcontent(blob)
    );
    const size_t size =
        static_cast<size_t>(git_blob_rawsize(blob));
    const std::string content =
        data != nullptr ? std::string(data, size) : "";

    git_blob_free(blob);
    git_index_free(index);
    git_repository_free(repository);
    return content;
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


void advanced_local_workflow(
    const fs::path& repository
) {
    fs::create_directories(repository);
    nexora::git::init_repository(repository.string());

    write_file(repository / "base.txt", "base\n");
    nexora::git::stage(repository.string(), {"base.txt"});
    nexora::git::commit(
        repository.string(),
        "Advanced base",
        kAuthor
    );

    write_file(
        repository / ".gitattributes",
        "*.bin filter=lfs diff=lfs merge=lfs -text\n"
    );
    const std::string lfs_content =
        "Nexora LFS native clean and smudge payload\n";
    write_file(repository / "asset.bin", lfs_content);
    nexora::git::stage(
        repository.string(),
        {".gitattributes", "asset.bin"}
    );

    const std::string lfs_staged =
        index_blob_content(
            repository,
            "asset.bin"
        );
    require(
        lfs_staged.find(
            "version https://git-lfs.github.com/spec/v1"
        ) != std::string::npos &&
            lfs_staged.find("oid sha256:") !=
                std::string::npos,
        "LFS clean filter did not stage a pointer"
    );

    nexora::git::commit(
        repository.string(),
        "Add LFS asset",
        kAuthor
    );

    fs::remove(repository / "asset.bin");
    nexora::git::checkout(repository.string(), "main");
    std::ifstream restored_input(
        repository / "asset.bin",
        std::ios::binary
    );
    const std::string restored{
        std::istreambuf_iterator<char>(restored_input),
        std::istreambuf_iterator<char>()
    };
    require(
        restored == lfs_content,
        "LFS smudge filter did not restore the local object"
    );

    nexora::git::create_branch(
        repository.string(),
        "source",
        ""
    );
    nexora::git::checkout(repository.string(), "source");
    write_file(repository / "picked.txt", "picked\n");
    nexora::git::stage(repository.string(), {"picked.txt"});
    const std::string source_commit =
        nexora::git::commit(
            repository.string(),
            "Pick this commit",
            kAuthor
        );
    const std::string source_oid =
        json_string(source_commit, "oid");
    require(
        !source_oid.empty(),
        "Cherry-pick source OID missing"
    );

    nexora::git::checkout(repository.string(), "main");
    const std::string picked =
        nexora::git::cherry_pick(
            repository.string(),
            source_oid,
            kAuthor
        );
    require(
        picked.find("\"state\":\"applied\"") !=
            std::string::npos &&
            fs::exists(repository / "picked.txt"),
        "Cherry-pick did not apply source commit"
    );

    write_file(repository / "stash.txt", "stashed\n");
    write_file(repository / "untracked-stash.txt", "untracked\n");
    const std::string stash_oid =
        nexora::git::save_stash(
            repository.string(),
            "Advanced stash",
            kAuthor,
            true
        );
    require(
        !stash_oid.empty() &&
            nexora::git::stashes(repository.string())
                .find("Advanced stash") != std::string::npos,
        "Stash was not persisted"
    );
    nexora::git::apply_stash(
        repository.string(),
        0,
        false
    );
    require(
        fs::exists(repository / "stash.txt"),
        "Stash apply did not restore changes"
    );
    nexora::git::reset_to(
        repository.string(),
        "HEAD",
        "hard"
    );
    if (fs::exists(repository / "stash.txt")) {
        fs::remove(repository / "stash.txt");
    }
    if (fs::exists(repository / "untracked-stash.txt")) {
        fs::remove(repository / "untracked-stash.txt");
    }
    nexora::git::drop_stash(repository.string(), 0);
    require(
        nexora::git::stashes(repository.string()) == "[]",
        "Stash drop did not empty stash list"
    );

    const std::string lightweight =
        nexora::git::create_tag(
            repository.string(),
            "v-local",
            "HEAD",
            "",
            kAuthor,
            false
        );
    const std::string annotated =
        nexora::git::create_tag(
            repository.string(),
            "v-annotated",
            "HEAD",
            "Advanced tag",
            kAuthor,
            true
        );
    const std::string listed_tags =
        nexora::git::tags(repository.string());
    require(
        !lightweight.empty() &&
            !annotated.empty() &&
            listed_tags.find("v-local") != std::string::npos &&
            listed_tags.find("v-annotated") != std::string::npos &&
            listed_tags.find("\"annotated\":true") !=
                std::string::npos,
        "Local tags were not listed"
    );
    nexora::git::delete_tag(
        repository.string(),
        "v-local"
    );
    nexora::git::delete_tag(
        repository.string(),
        "v-annotated"
    );

    write_file(repository / "revert.txt", "remove me\n");
    nexora::git::stage(repository.string(), {"revert.txt"});
    const std::string revert_source =
        nexora::git::commit(
            repository.string(),
            "Revert source",
            kAuthor
        );
    const std::string revert_oid =
        json_string(revert_source, "oid");
    const std::string reverted =
        nexora::git::revert_commit(
            repository.string(),
            revert_oid,
            kAuthor
        );
    require(
        reverted.find("\"state\":\"applied\"") !=
            std::string::npos &&
            !fs::exists(repository / "revert.txt"),
        "Revert did not invert source commit"
    );

    write_file(repository / "reset.txt", "one\n");
    nexora::git::stage(repository.string(), {"reset.txt"});
    const std::string reset_base =
        nexora::git::commit(
            repository.string(),
            "Reset base",
            kAuthor
        );
    const std::string reset_base_oid =
        json_string(reset_base, "oid");
    write_file(repository / "reset.txt", "two\n");
    nexora::git::stage(repository.string(), {"reset.txt"});
    nexora::git::commit(
        repository.string(),
        "Reset target",
        kAuthor
    );
    nexora::git::reset_to(
        repository.string(),
        reset_base_oid,
        "soft"
    );
    require(
        nexora::git::diff(
            repository.string(),
            "staged"
        ).find("two") != std::string::npos,
        "Soft reset did not preserve staged changes"
    );
    nexora::git::reset_to(
        repository.string(),
        reset_base_oid,
        "hard"
    );

    nexora::git::create_branch(
        repository.string(),
        "topic-rebase",
        ""
    );
    nexora::git::checkout(
        repository.string(),
        "topic-rebase"
    );
    write_file(repository / "topic.txt", "topic\n");
    nexora::git::stage(repository.string(), {"topic.txt"});
    nexora::git::commit(
        repository.string(),
        "Topic before rebase",
        kAuthor
    );

    nexora::git::checkout(repository.string(), "main");
    write_file(repository / "main-advance.txt", "main\n");
    nexora::git::stage(
        repository.string(),
        {"main-advance.txt"}
    );
    nexora::git::commit(
        repository.string(),
        "Advance main",
        kAuthor
    );

    nexora::git::checkout(
        repository.string(),
        "topic-rebase"
    );
    const std::string rebased =
        nexora::git::rebase_onto(
            repository.string(),
            "main",
            kAuthor
        );
    require(
        rebased.find("\"state\":\"rebased\"") !=
            std::string::npos &&
            fs::exists(repository / "main-advance.txt") &&
            fs::exists(repository / "topic.txt"),
        "Explicit rebase did not replay topic onto main"
    );

    require(
        nexora::git::submodules(repository.string()) == "[]",
        "Repository without submodules did not return an empty list"
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
    const fs::path advanced_local = root / "advanced-local";

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
        advanced_local_workflow(advanced_local);

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
