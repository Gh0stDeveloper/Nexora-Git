#include "nexora/git_core.h"

#include <git2.h>

#include <algorithm>
#include <cctype>
#include <cstdio>
#include <mutex>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

namespace nexora::git {
namespace {

std::once_flag init_flag;

std::string json_escape(const std::string& value) {
    std::ostringstream out;
    for (unsigned char c : value) {
        switch (c) {
            case '"': out << "\\\""; break;
            case '\\': out << "\\\\"; break;
            case '\b': out << "\\b"; break;
            case '\f': out << "\\f"; break;
            case '\n': out << "\\n"; break;
            case '\r': out << "\\r"; break;
            case '\t': out << "\\t"; break;
            default:
                if (c < 0x20) {
                    const char* hex = "0123456789abcdef";
                    out << "\\u00" << hex[(c >> 4) & 0x0f] << hex[c & 0x0f];
                } else {
                    out << static_cast<char>(c);
                }
        }
    }
    return out.str();
}

std::string quote(const std::string& value) {
    return "\"" + json_escape(value) + "\"";
}

[[noreturn]] void throw_git_error(int code, const std::string& operation) {
    const git_error* last = git_error_last();
    const int klass = last != nullptr ? last->klass : 0;
    const std::string detail =
        (last != nullptr && last->message != nullptr)
            ? last->message
            : "Unknown libgit2 error";
    throw GitError(code, klass, operation + ": " + detail);
}

void check(int code, const std::string& operation) {
    if (code < 0) {
        throw_git_error(code, operation);
    }
}

git_repository* open_repository(const std::string& path) {
    git_repository* repository = nullptr;
    check(
        git_repository_open(&repository, path.c_str()),
        "Open repository"
    );
    return repository;
}

std::string oid_to_string(const git_oid* oid) {
    if (oid == nullptr) return "";
    char buffer[GIT_OID_MAX_HEXSIZE + 1] = {0};
    git_oid_tostr(buffer, sizeof(buffer), oid);
    return buffer;
}

std::string current_branch_name(git_repository* repository) {
    git_reference* head = nullptr;
    const int rc = git_repository_head(&head, repository);

    if (rc == GIT_EUNBORNBRANCH || rc == GIT_ENOTFOUND) {
        git_reference* symbolic_head = nullptr;
        const int symbolic_rc = git_reference_lookup(
            &symbolic_head,
            repository,
            "HEAD"
        );

        if (symbolic_rc < 0) {
            return "";
        }

        const char* target =
            git_reference_symbolic_target(symbolic_head);
        std::string result = target != nullptr ? target : "";
        git_reference_free(symbolic_head);

        const std::string prefix = "refs/heads/";
        if (result.rfind(prefix, 0) == 0) {
            result.erase(0, prefix.size());
        }
        return result;
    }

    check(rc, "Read HEAD");

    const char* shorthand = git_reference_shorthand(head);
    std::string result = shorthand != nullptr ? shorthand : "";
    git_reference_free(head);
    return result;
}

std::string repository_json(git_repository* repository) {
    const char* workdir = git_repository_workdir(repository);
    const bool bare = git_repository_is_bare(repository) != 0;

    std::ostringstream out;
    out << "{";
    out << "\"path\":" << quote(workdir != nullptr ? workdir : "");
    out << ",\"bare\":" << (bare ? "true" : "false");
    out << ",\"head\":" << quote(current_branch_name(repository));
    out << "}";
    return out.str();
}

bool is_github_https_url(const char* raw_url) {
    if (raw_url == nullptr) return false;

    const std::string url(raw_url);
    const std::string origin = "https://github.com";

    if (url == origin) return true;
    if (url.rfind(origin + "/", 0) != 0) return false;

    return true;
}

int credentials_callback(
    git_credential** out,
    const char* url,
    const char* username_from_url,
    unsigned int allowed_types,
    void* payload
) {
    const auto* credentials = static_cast<const Credentials*>(payload);
    if (credentials == nullptr || credentials->empty()) {
        return GIT_PASSTHROUGH;
    }

    if (!is_github_https_url(url)) {
        return GIT_PASSTHROUGH;
    }

    if ((allowed_types & GIT_CREDENTIAL_USERPASS_PLAINTEXT) != 0) {
        const std::string username =
            !credentials->username.empty()
                ? credentials->username
                : "x-access-token";
        return git_credential_userpass_plaintext_new(
            out,
            username.c_str(),
            credentials->password.c_str()
        );
    }

    if ((allowed_types & GIT_CREDENTIAL_USERNAME) != 0) {
        const char* username =
            !credentials->username.empty()
                ? credentials->username.c_str()
                : (username_from_url != nullptr
                    ? username_from_url
                    : "x-access-token");
        return git_credential_username_new(out, username);
    }

    return GIT_PASSTHROUGH;
}

git_remote_callbacks remote_callbacks(const Credentials* credentials) {
    git_remote_callbacks callbacks = GIT_REMOTE_CALLBACKS_INIT;
    callbacks.credentials = credentials_callback;
    callbacks.payload = const_cast<Credentials*>(credentials);
    return callbacks;
}

int collect_merge_head(
    const git_oid* oid,
    void* payload
) {
    if (oid == nullptr || payload == nullptr) {
        return 0;
    }

    auto* heads =
        static_cast<std::vector<git_oid>*>(payload);
    heads->push_back(*oid);
    return 0;
}

git_tree* head_tree(git_repository* repository) {
    git_reference* head = nullptr;
    int rc = git_repository_head(&head, repository);
    if (rc == GIT_EUNBORNBRANCH || rc == GIT_ENOTFOUND) {
        return nullptr;
    }
    check(rc, "Read HEAD");

    git_commit* commit = nullptr;
    rc = git_reference_peel(
        reinterpret_cast<git_object**>(&commit),
        head,
        GIT_OBJECT_COMMIT
    );
    git_reference_free(head);
    check(rc, "Resolve HEAD commit");

    git_tree* tree = nullptr;
    rc = git_commit_tree(&tree, commit);
    git_commit_free(commit);
    check(rc, "Read HEAD tree");
    return tree;
}

bool repository_has_conflicts(git_repository* repository) {
    git_index* index = nullptr;
    check(git_repository_index(&index, repository), "Open repository index");
    const bool conflicts = git_index_has_conflicts(index) != 0;
    git_index_free(index);
    return conflicts;
}

std::string conflicts_json(git_repository* repository) {
    git_index* index = nullptr;
    check(git_repository_index(&index, repository), "Open repository index");

    git_index_conflict_iterator* iterator = nullptr;
    check(
        git_index_conflict_iterator_new(&iterator, index),
        "Create conflict iterator"
    );

    std::ostringstream out;
    out << "[";
    bool first = true;

    const git_index_entry* ancestor = nullptr;
    const git_index_entry* ours = nullptr;
    const git_index_entry* theirs = nullptr;

    while (true) {
        const int rc = git_index_conflict_next(
            &ancestor,
            &ours,
            &theirs,
            iterator
        );
        if (rc == GIT_ITEROVER) break;
        check(rc, "Read conflict");

        if (!first) out << ",";
        first = false;

        const std::string path =
            ours != nullptr && ours->path != nullptr
                ? ours->path
                : (theirs != nullptr && theirs->path != nullptr
                    ? theirs->path
                    : (ancestor != nullptr && ancestor->path != nullptr
                        ? ancestor->path
                        : ""));

        out << "{";
        out << "\"path\":" << quote(path);
        out << ",\"ancestor\":"
            << quote(
                ancestor != nullptr && ancestor->path != nullptr
                    ? ancestor->path
                    : ""
            );
        out << ",\"ours\":"
            << quote(
                ours != nullptr && ours->path != nullptr
                    ? ours->path
                    : ""
            );
        out << ",\"theirs\":"
            << quote(
                theirs != nullptr && theirs->path != nullptr
                    ? theirs->path
                    : ""
            );
        out << "}";
    }

    out << "]";
    git_index_conflict_iterator_free(iterator);
    git_index_free(index);
    return out.str();
}

std::string status_path(const git_status_entry* entry) {
    const git_diff_delta* delta = nullptr;

    if (entry->index_to_workdir != nullptr) {
        delta = entry->index_to_workdir;
    } else if (entry->head_to_index != nullptr) {
        delta = entry->head_to_index;
    }

    if (delta == nullptr) return "";
    if (delta->new_file.path != nullptr) return delta->new_file.path;
    if (delta->old_file.path != nullptr) return delta->old_file.path;
    return "";
}

git_signature* make_signature(const Author& author) {
    if (author.name.empty() || author.email.empty()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Author name and email are required"
        );
    }

    git_signature* signature = nullptr;
    check(
        git_signature_now(
            &signature,
            author.name.c_str(),
            author.email.c_str()
        ),
        "Create commit signature"
    );
    return signature;
}

git_commit* resolve_commit(
    git_repository* repository,
    const std::string& revspec
) {
    git_object* object = nullptr;

    if (revspec.empty()) {
        git_reference* head = nullptr;
        check(git_repository_head(&head, repository), "Read HEAD");
        check(
            git_reference_peel(&object, head, GIT_OBJECT_COMMIT),
            "Resolve HEAD commit"
        );
        git_reference_free(head);
    } else {
        check(
            git_revparse_single(&object, repository, revspec.c_str()),
            "Resolve revision"
        );

        if (git_object_type(object) != GIT_OBJECT_COMMIT) {
            git_object* peeled = nullptr;
            const int rc = git_object_peel(
                &peeled,
                object,
                GIT_OBJECT_COMMIT
            );
            git_object_free(object);
            check(rc, "Peel revision to commit");
            object = peeled;
        }
    }

    return reinterpret_cast<git_commit*>(object);
}

std::string merge_result_json(
    const std::string& state,
    const std::string& oid,
    const std::string& conflict_payload
) {
    std::ostringstream out;
    out << "{";
    out << "\"state\":" << quote(state);
    out << ",\"commitOid\":" << quote(oid);
    out << ",\"conflicts\":" << conflict_payload;
    out << "}";
    return out.str();
}

std::string fast_forward(
    git_repository* repository,
    git_reference* local_reference,
    const git_annotated_commit* target
) {
    const git_oid* target_oid = git_annotated_commit_id(target);

    git_object* target_object = nullptr;
    check(
        git_object_lookup(
            &target_object,
            repository,
            target_oid,
            GIT_OBJECT_COMMIT
        ),
        "Resolve fast-forward target"
    );

    git_checkout_options checkout_options = GIT_CHECKOUT_OPTIONS_INIT;
    checkout_options.checkout_strategy =
        GIT_CHECKOUT_SAFE | GIT_CHECKOUT_RECREATE_MISSING;

    const int checkout_rc = git_checkout_tree(
        repository,
        target_object,
        &checkout_options
    );
    git_object_free(target_object);
    check(checkout_rc, "Checkout fast-forward target");

    git_reference* updated = nullptr;
    check(
        git_reference_set_target(
            &updated,
            local_reference,
            target_oid,
            "Nexora Git fast-forward"
        ),
        "Update branch for fast-forward"
    );

    const char* updated_name = git_reference_name(updated);
    if (updated_name != nullptr) {
        check(
            git_repository_set_head(repository, updated_name),
            "Update HEAD after fast-forward"
        );
    }

    git_reference_free(updated);

    check(
        git_checkout_head(repository, &checkout_options),
        "Refresh working tree after fast-forward"
    );

    return merge_result_json(
        "fast_forward",
        oid_to_string(target_oid),
        "[]"
    );
}

std::string merge_annotated(
    git_repository* repository,
    const git_annotated_commit* target,
    const Author& author,
    const std::string& message
) {
    git_merge_analysis_t analysis = GIT_MERGE_ANALYSIS_NONE;
    git_merge_preference_t preference = GIT_MERGE_PREFERENCE_NONE;
    const git_annotated_commit* heads[] = {target};

    check(
        git_merge_analysis(
            &analysis,
            &preference,
            repository,
            heads,
            1
        ),
        "Analyze merge"
    );

    if ((analysis & GIT_MERGE_ANALYSIS_UP_TO_DATE) != 0) {
        return merge_result_json("up_to_date", "", "[]");
    }

    git_reference* head_reference = nullptr;
    const int head_rc = git_repository_head(&head_reference, repository);

    if ((analysis & GIT_MERGE_ANALYSIS_FASTFORWARD) != 0 ||
        (analysis & GIT_MERGE_ANALYSIS_UNBORN) != 0) {
        if (head_rc < 0) {
            throw_git_error(head_rc, "Read local branch for fast-forward");
        }
        const std::string result =
            fast_forward(repository, head_reference, target);
        git_reference_free(head_reference);
        return result;
    }

    if (head_rc < 0) {
        throw_git_error(head_rc, "Read HEAD before merge");
    }

    git_merge_options merge_options = GIT_MERGE_OPTIONS_INIT;
    git_checkout_options checkout_options = GIT_CHECKOUT_OPTIONS_INIT;
    checkout_options.checkout_strategy =
        GIT_CHECKOUT_SAFE |
        GIT_CHECKOUT_RECREATE_MISSING |
        GIT_CHECKOUT_ALLOW_CONFLICTS |
        GIT_CHECKOUT_CONFLICT_STYLE_MERGE;

    check(
        git_merge(
            repository,
            heads,
            1,
            &merge_options,
            &checkout_options
        ),
        "Merge branches"
    );

    if (repository_has_conflicts(repository)) {
        git_reference_free(head_reference);
        return merge_result_json(
            "conflicts",
            "",
            conflicts_json(repository)
        );
    }

    git_index* index = nullptr;
    check(git_repository_index(&index, repository), "Open merge index");

    git_oid tree_oid{};
    check(git_index_write_tree(&tree_oid, index), "Write merge tree");
    check(git_index_write(index), "Write merge index");

    git_tree* tree = nullptr;
    check(git_tree_lookup(&tree, repository, &tree_oid), "Open merge tree");

    git_commit* local_commit = nullptr;
    check(
        git_reference_peel(
            reinterpret_cast<git_object**>(&local_commit),
            head_reference,
            GIT_OBJECT_COMMIT
        ),
        "Resolve local merge parent"
    );

    git_commit* incoming_commit = nullptr;
    check(
        git_commit_lookup(
            &incoming_commit,
            repository,
            git_annotated_commit_id(target)
        ),
        "Resolve incoming merge parent"
    );

    git_signature* signature = make_signature(author);
    const git_commit* parents[] = {local_commit, incoming_commit};
    git_oid commit_oid{};

    const int commit_rc = git_commit_create(
        &commit_oid,
        repository,
        "HEAD",
        signature,
        signature,
        nullptr,
        message.c_str(),
        tree,
        2,
        parents
    );

    git_signature_free(signature);
    git_commit_free(incoming_commit);
    git_commit_free(local_commit);
    git_tree_free(tree);
    git_index_free(index);
    git_reference_free(head_reference);

    check(commit_rc, "Create merge commit");
    check(
        git_repository_state_cleanup(repository),
        "Clean repository merge state"
    );

    return merge_result_json(
        "merged",
        oid_to_string(&commit_oid),
        "[]"
    );
}


git_reference* resolve_pull_upstream(
    git_repository* repository,
    git_reference* head,
    const std::string& remote
) {
    git_reference* upstream = nullptr;
    int rc = git_branch_upstream(&upstream, head);

    if (rc == 0) {
        const char* shorthand =
            git_reference_shorthand(upstream);
        const std::string configured =
            shorthand != nullptr ? shorthand : "";

        if (configured.rfind(remote + "/", 0) == 0) {
            return upstream;
        }

        git_reference_free(upstream);
        upstream = nullptr;
    } else if (rc != GIT_ENOTFOUND) {
        throw_git_error(rc, "Resolve configured upstream");
    }

    const char* branch_name = git_reference_shorthand(head);
    const std::string tracking =
        "refs/remotes/" + remote + "/" +
        (branch_name != nullptr ? branch_name : "");

    rc = git_reference_lookup(
        &upstream,
        repository,
        tracking.c_str()
    );

    if (rc < 0) {
        throw_git_error(rc, "Resolve pull upstream");
    }

    return upstream;
}

std::string fast_forward_only(
    git_repository* repository,
    git_reference* head,
    const git_annotated_commit* target
) {
    git_merge_analysis_t analysis = GIT_MERGE_ANALYSIS_NONE;
    git_merge_preference_t preference = GIT_MERGE_PREFERENCE_NONE;
    const git_annotated_commit* heads[] = {target};

    check(
        git_merge_analysis(
            &analysis,
            &preference,
            repository,
            heads,
            1
        ),
        "Analyze fast-forward pull"
    );

    if ((analysis & GIT_MERGE_ANALYSIS_UP_TO_DATE) != 0) {
        return merge_result_json("up_to_date", "", "[]");
    }

    if ((analysis & GIT_MERGE_ANALYSIS_FASTFORWARD) != 0 ||
        (analysis & GIT_MERGE_ANALYSIS_UNBORN) != 0) {
        return fast_forward(repository, head, target);
    }

    throw GitError(
        GIT_ENONFASTFORWARD,
        0,
        "Fast-forward-only pull rejected because local and upstream histories diverged"
    );
}

std::string advance_rebase(
    git_repository* repository,
    git_rebase* rebase,
    const Author& author,
    bool commit_current
) {
    git_signature* signature = make_signature(author);
    git_oid last_oid{};
    bool has_last_oid = false;

    auto commit_operation = [&]() {
        if (repository_has_conflicts(repository)) {
            return false;
        }

        git_oid oid{};
        const int rc = git_rebase_commit(
            &oid,
            rebase,
            nullptr,
            signature,
            nullptr,
            nullptr
        );

        if (rc == GIT_EAPPLIED) {
            return true;
        }
        if (rc < 0) {
            git_signature_free(signature);
            git_rebase_free(rebase);
            throw_git_error(rc, "Commit rebased change");
        }

        last_oid = oid;
        has_last_oid = true;
        return true;
    };

    if (commit_current && !commit_operation()) {
        const std::string payload = conflicts_json(repository);
        git_signature_free(signature);
        git_rebase_free(rebase);
        return merge_result_json("conflicts", "", payload);
    }

    while (true) {
        git_rebase_operation* operation = nullptr;
        const int next_rc = git_rebase_next(
            &operation,
            rebase
        );

        if (next_rc == GIT_ITEROVER) {
            const int finish_rc = git_rebase_finish(
                rebase,
                signature
            );
            git_signature_free(signature);
            git_rebase_free(rebase);
            check(finish_rc, "Finish rebase");

            return merge_result_json(
                "rebased",
                has_last_oid
                    ? oid_to_string(&last_oid)
                    : "",
                "[]"
            );
        }

        if (next_rc < 0) {
            git_signature_free(signature);
            git_rebase_free(rebase);
            throw_git_error(next_rc, "Apply next rebase operation");
        }

        if (repository_has_conflicts(repository)) {
            const std::string payload = conflicts_json(repository);
            git_signature_free(signature);
            git_rebase_free(rebase);
            return merge_result_json(
                "conflicts",
                "",
                payload
            );
        }

        if (!commit_operation()) {
            const std::string payload = conflicts_json(repository);
            git_signature_free(signature);
            git_rebase_free(rebase);
            return merge_result_json(
                "conflicts",
                "",
                payload
            );
        }
    }
}

}  // namespace

GitError::GitError(int code, int klass, std::string message)
    : code_(code),
      klass_(klass),
      message_(std::move(message)) {}

const char* GitError::what() const noexcept {
    return message_.c_str();
}

int GitError::code() const noexcept {
    return code_;
}

int GitError::klass() const noexcept {
    return klass_;
}

void initialize(
    const std::string& home_directory,
    const std::string& certificate_directory
) {
    std::call_once(init_flag, [&]() {
        check(git_libgit2_init(), "Initialize libgit2");
        register_lfs_filter();

        if (!home_directory.empty()) {
            check(
                git_libgit2_opts(
                    GIT_OPT_SET_HOMEDIR,
                    home_directory.c_str()
                ),
                "Configure libgit2 home directory"
            );
        }

        if (!certificate_directory.empty()) {
            check(
                git_libgit2_opts(
                    GIT_OPT_SET_SSL_CERT_LOCATIONS,
                    nullptr,
                    certificate_directory.c_str()
                ),
                "Configure Android CA directory"
            );
        }

        git_libgit2_opts(
            GIT_OPT_SET_USER_AGENT_PRODUCT,
            "NexoraGit/0.1"
        );
    });
}

std::string version() {
    int major = 0;
    int minor = 0;
    int revision = 0;
    git_libgit2_version(&major, &minor, &revision);

    return std::to_string(major) + "." +
        std::to_string(minor) + "." +
        std::to_string(revision);
}

std::string init_repository(const std::string& path) {
    git_repository* repository = nullptr;
    check(
        git_repository_init(&repository, path.c_str(), 0),
        "Initialize repository"
    );

    const int set_head_rc = git_repository_set_head(
        repository,
        "refs/heads/main"
    );
    if (set_head_rc < 0 && set_head_rc != GIT_EUNBORNBRANCH) {
        git_repository_free(repository);
        throw_git_error(set_head_rc, "Set default branch");
    }

    const std::string result = repository_json(repository);
    git_repository_free(repository);
    return result;
}

std::string clone_repository(
    const std::string& url,
    const std::string& destination,
    const Credentials& credentials
) {
    if (url.empty()) {
        throw GitError(GIT_EINVALID, 0, "Clone URL is required");
    }

    git_clone_options options = GIT_CLONE_OPTIONS_INIT;
    options.fetch_opts.callbacks = remote_callbacks(&credentials);
    options.checkout_opts.checkout_strategy =
        GIT_CHECKOUT_SAFE | GIT_CHECKOUT_RECREATE_MISSING;

    git_repository* repository = nullptr;
    check(
        git_clone(
            &repository,
            url.c_str(),
            destination.c_str(),
            &options
        ),
        "Clone repository"
    );

    const std::string result = repository_json(repository);
    git_repository_free(repository);
    return result;
}

std::string remote_url(
    const std::string& repository_path,
    const std::string& remote_name
) {
    git_repository* repository = open_repository(repository_path);
    git_remote* remote = nullptr;
    const std::string name = remote_name.empty() ? "origin" : remote_name;

    const int rc = git_remote_lookup(
        &remote,
        repository,
        name.c_str()
    );
    if (rc < 0) {
        git_repository_free(repository);
        throw_git_error(rc, "Open remote");
    }

    const char* url = git_remote_url(remote);
    const std::string result = url != nullptr ? url : "";

    git_remote_free(remote);
    git_repository_free(repository);
    return result;
}

std::string remotes(const std::string& repository_path) {
    git_repository* repository = open_repository(repository_path);
    git_strarray names{};
    check(
        git_remote_list(&names, repository),
        "List remotes"
    );

    std::ostringstream out;
    out << "[";
    for (size_t index = 0; index < names.count; ++index) {
        if (index > 0) out << ",";

        const std::string name =
            names.strings[index] != nullptr
                ? names.strings[index]
                : "";

        git_remote* remote = nullptr;
        const int lookup_rc = git_remote_lookup(
            &remote,
            repository,
            name.c_str()
        );
        if (lookup_rc < 0) {
            git_strarray_dispose(&names);
            git_repository_free(repository);
            throw_git_error(lookup_rc, "Open listed remote");
        }

        const char* raw_url = git_remote_url(remote);
        out << "{";
        out << "\"name\":" << quote(name);
        out << ",\"url\":"
            << quote(raw_url != nullptr ? raw_url : "");
        out << "}";

        git_remote_free(remote);
    }
    out << "]";

    git_strarray_dispose(&names);
    git_repository_free(repository);
    return out.str();
}

void add_remote(
    const std::string& repository_path,
    const std::string& name,
    const std::string& url
) {
    if (name.empty() || url.empty()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Remote name and URL are required"
        );
    }

    git_repository* repository = open_repository(repository_path);
    git_remote* remote = nullptr;
    const int rc = git_remote_create(
        &remote,
        repository,
        name.c_str(),
        url.c_str()
    );

    if (remote != nullptr) git_remote_free(remote);
    git_repository_free(repository);
    check(rc, "Create remote");
}

void rename_remote(
    const std::string& repository_path,
    const std::string& old_name,
    const std::string& new_name
) {
    if (old_name.empty() || new_name.empty()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Remote names are required"
        );
    }

    git_repository* repository = open_repository(repository_path);
    git_strarray problems{};
    const int rc = git_remote_rename(
        &problems,
        repository,
        old_name.c_str(),
        new_name.c_str()
    );

    if (rc == 0) {
        git_strarray_dispose(&problems);
    }
    git_repository_free(repository);
    check(rc, "Rename remote");
}

void remove_remote(
    const std::string& repository_path,
    const std::string& name
) {
    if (name.empty()) {
        throw GitError(GIT_EINVALID, 0, "Remote name is required");
    }

    git_repository* repository = open_repository(repository_path);
    const int rc = git_remote_delete(
        repository,
        name.c_str()
    );
    git_repository_free(repository);
    check(rc, "Delete remote");
}

std::string status(const std::string& repository_path) {
    git_repository* repository = open_repository(repository_path);

    git_status_options options = GIT_STATUS_OPTIONS_INIT;
    options.show = GIT_STATUS_SHOW_INDEX_AND_WORKDIR;
    options.flags =
        GIT_STATUS_OPT_INCLUDE_UNTRACKED |
        GIT_STATUS_OPT_RECURSE_UNTRACKED_DIRS |
        GIT_STATUS_OPT_RENAMES_HEAD_TO_INDEX |
        GIT_STATUS_OPT_RENAMES_INDEX_TO_WORKDIR |
        GIT_STATUS_OPT_SORT_CASE_SENSITIVELY;

    git_status_list* status_list = nullptr;
    const int list_rc = git_status_list_new(
        &status_list,
        repository,
        &options
    );
    if (list_rc < 0) {
        git_repository_free(repository);
        throw_git_error(list_rc, "Read repository status");
    }

    std::ostringstream out;
    out << "{";
    out << "\"branch\":" << quote(current_branch_name(repository));
    out << ",\"entries\":[";

    const size_t count = git_status_list_entrycount(status_list);
    for (size_t index = 0; index < count; ++index) {
        const git_status_entry* entry =
            git_status_byindex(status_list, index);
        if (entry == nullptr) continue;

        if (index > 0) out << ",";

        const unsigned int flags = entry->status;
        const bool staged =
            (flags & (
                GIT_STATUS_INDEX_NEW |
                GIT_STATUS_INDEX_MODIFIED |
                GIT_STATUS_INDEX_DELETED |
                GIT_STATUS_INDEX_RENAMED |
                GIT_STATUS_INDEX_TYPECHANGE
            )) != 0;
        const bool working =
            (flags & (
                GIT_STATUS_WT_NEW |
                GIT_STATUS_WT_MODIFIED |
                GIT_STATUS_WT_DELETED |
                GIT_STATUS_WT_TYPECHANGE |
                GIT_STATUS_WT_RENAMED |
                GIT_STATUS_WT_UNREADABLE
            )) != 0;

        out << "{";
        out << "\"path\":" << quote(status_path(entry));
        out << ",\"flags\":" << flags;
        out << ",\"staged\":" << (staged ? "true" : "false");
        out << ",\"workingTree\":" << (working ? "true" : "false");
        out << ",\"untracked\":"
            << ((flags & GIT_STATUS_WT_NEW) != 0 ? "true" : "false");
        out << ",\"conflicted\":"
            << ((flags & GIT_STATUS_CONFLICTED) != 0 ? "true" : "false");
        out << "}";
    }

    out << "]";
    out << ",\"conflicted\":"
        << (repository_has_conflicts(repository) ? "true" : "false");
    out << "}";

    git_status_list_free(status_list);
    git_repository_free(repository);
    return out.str();
}

void stage(
    const std::string& repository_path,
    const std::vector<std::string>& paths
) {
    git_repository* repository = open_repository(repository_path);
    git_index* index = nullptr;

    const int index_rc = git_repository_index(&index, repository);
    if (index_rc < 0) {
        git_repository_free(repository);
        throw_git_error(index_rc, "Open repository index");
    }

    for (const auto& path : paths) {
        if (path.empty()) continue;

        int rc = git_index_add_bypath(index, path.c_str());
        if (rc == GIT_ENOTFOUND) {
            rc = git_index_remove_bypath(index, path.c_str());
        }
        if (rc < 0 && rc != GIT_ENOTFOUND) {
            git_index_free(index);
            git_repository_free(repository);
            throw_git_error(rc, "Stage path");
        }
    }

    const int write_rc = git_index_write(index);
    git_index_free(index);
    git_repository_free(repository);
    check(write_rc, "Write repository index");
}

void unstage(
    const std::string& repository_path,
    const std::vector<std::string>& paths
) {
    if (paths.empty()) {
        return;
    }

    git_repository* repository = open_repository(repository_path);

    std::vector<char*> raw_paths;
    raw_paths.reserve(paths.size());
    for (const auto& path : paths) {
        raw_paths.push_back(const_cast<char*>(path.c_str()));
    }

    git_strarray pathspec{};
    pathspec.strings = raw_paths.data();
    pathspec.count = raw_paths.size();

    git_object* head = nullptr;
    int rc = git_revparse_single(&head, repository, "HEAD");

    if (rc == GIT_EUNBORNBRANCH || rc == GIT_ENOTFOUND) {
        git_index* index = nullptr;
        check(git_repository_index(&index, repository), "Open repository index");
        for (const auto& path : paths) {
            const int remove_rc = git_index_remove_bypath(
                index,
                path.c_str()
            );
            if (remove_rc < 0 && remove_rc != GIT_ENOTFOUND) {
                git_index_free(index);
                git_repository_free(repository);
                throw_git_error(remove_rc, "Unstage initial path");
            }
        }
        check(git_index_write(index), "Write repository index");
        git_index_free(index);
        git_repository_free(repository);
        return;
    }

    if (rc < 0) {
        git_repository_free(repository);
        throw_git_error(rc, "Resolve HEAD for unstage");
    }

    rc = git_reset_default(repository, head, &pathspec);
    git_object_free(head);
    git_repository_free(repository);
    check(rc, "Unstage paths");
}

std::string commit(
    const std::string& repository_path,
    const std::string& message,
    const Author& author
) {
    if (message.empty()) {
        throw GitError(GIT_EINVALID, 0, "Commit message is required");
    }

    git_repository* repository = open_repository(repository_path);
    git_index* index = nullptr;
    check(git_repository_index(&index, repository), "Open repository index");

    git_oid tree_oid{};
    check(git_index_write_tree(&tree_oid, index), "Write commit tree");
    check(git_index_write(index), "Write repository index");

    git_tree* tree = nullptr;
    check(git_tree_lookup(&tree, repository, &tree_oid), "Open commit tree");

    git_signature* signature = make_signature(author);

    git_commit* parent = nullptr;
    git_reference* head = nullptr;
    const int head_rc = git_repository_head(&head, repository);

    size_t parent_count = 0;
    const git_commit* parents[1] = {nullptr};

    if (head_rc == 0) {
        check(
            git_reference_peel(
                reinterpret_cast<git_object**>(&parent),
                head,
                GIT_OBJECT_COMMIT
            ),
            "Resolve commit parent"
        );
        parent_count = 1;
        parents[0] = parent;
    } else if (
        head_rc != GIT_EUNBORNBRANCH &&
        head_rc != GIT_ENOTFOUND
    ) {
        git_signature_free(signature);
        git_tree_free(tree);
        git_index_free(index);
        git_repository_free(repository);
        throw_git_error(head_rc, "Read HEAD before commit");
    }

    git_oid commit_oid{};
    const int commit_rc = git_commit_create(
        &commit_oid,
        repository,
        "HEAD",
        signature,
        signature,
        nullptr,
        message.c_str(),
        tree,
        parent_count,
        parent_count == 0 ? nullptr : parents
    );

    if (head != nullptr) git_reference_free(head);
    if (parent != nullptr) git_commit_free(parent);
    git_signature_free(signature);
    git_tree_free(tree);
    git_index_free(index);
    git_repository_free(repository);

    check(commit_rc, "Create commit");

    std::ostringstream out;
    out << "{";
    out << "\"oid\":" << quote(oid_to_string(&commit_oid));
    out << ",\"message\":" << quote(message);
    out << "}";
    return out.str();
}

std::string branches(const std::string& repository_path) {
    git_repository* repository = open_repository(repository_path);
    git_branch_iterator* iterator = nullptr;
    check(
        git_branch_iterator_new(&iterator, repository, GIT_BRANCH_ALL),
        "Create branch iterator"
    );

    std::ostringstream out;
    out << "[";
    bool first = true;

    while (true) {
        git_reference* reference = nullptr;
        git_branch_t type = GIT_BRANCH_LOCAL;
        const int rc = git_branch_next(
            &reference,
            &type,
            iterator
        );

        if (rc == GIT_ITEROVER) break;
        check(rc, "Read branch");

        const char* name = nullptr;
        check(git_branch_name(&name, reference), "Read branch name");

        git_reference* upstream = nullptr;
        std::string upstream_name;
        if (type == GIT_BRANCH_LOCAL &&
            git_branch_upstream(&upstream, reference) == 0) {
            const char* shorthand = git_reference_shorthand(upstream);
            if (shorthand != nullptr) {
                upstream_name = shorthand;
            }
            git_reference_free(upstream);
        }

        if (!first) out << ",";
        first = false;

        out << "{";
        out << "\"name\":" << quote(name != nullptr ? name : "");
        out << ",\"remote\":"
            << (type == GIT_BRANCH_REMOTE ? "true" : "false");
        out << ",\"head\":"
            << (git_branch_is_head(reference) ? "true" : "false");
        out << ",\"upstream\":" << quote(upstream_name);
        out << "}";

        git_reference_free(reference);
    }

    out << "]";

    git_branch_iterator_free(iterator);
    git_repository_free(repository);
    return out.str();
}

void set_upstream(
    const std::string& repository_path,
    const std::string& branch_name,
    const std::string& upstream
) {
    if (branch_name.empty()) {
        throw GitError(GIT_EINVALID, 0, "Local branch is required");
    }

    git_repository* repository = open_repository(repository_path);
    git_reference* branch = nullptr;
    const int lookup_rc = git_branch_lookup(
        &branch,
        repository,
        branch_name.c_str(),
        GIT_BRANCH_LOCAL
    );
    if (lookup_rc < 0) {
        git_repository_free(repository);
        throw_git_error(lookup_rc, "Open local branch");
    }

    const int rc = git_branch_set_upstream(
        branch,
        upstream.empty() ? nullptr : upstream.c_str()
    );

    git_reference_free(branch);
    git_repository_free(repository);
    check(rc, upstream.empty() ? "Unset upstream" : "Set upstream");
}

std::string divergence(
    const std::string& repository_path,
    const std::string& local_ref,
    const std::string& upstream_ref
) {
    if (local_ref.empty() || upstream_ref.empty()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Local and upstream refs are required"
        );
    }

    git_repository* repository = open_repository(repository_path);
    git_commit* local = nullptr;
    git_commit* upstream = nullptr;

    try {
        local = resolve_commit(repository, local_ref);
        upstream = resolve_commit(repository, upstream_ref);
    } catch (...) {
        if (local != nullptr) git_commit_free(local);
        if (upstream != nullptr) git_commit_free(upstream);
        git_repository_free(repository);
        throw;
    }

    size_t ahead = 0;
    size_t behind = 0;
    const int rc = git_graph_ahead_behind(
        &ahead,
        &behind,
        repository,
        git_commit_id(local),
        git_commit_id(upstream)
    );

    if (rc < 0) {
        git_commit_free(upstream);
        git_commit_free(local);
        git_repository_free(repository);
        throw_git_error(rc, "Calculate branch divergence");
    }

    std::ostringstream out;
    out << "{";
    out << "\"localRef\":" << quote(local_ref);
    out << ",\"upstreamRef\":" << quote(upstream_ref);
    out << ",\"localOid\":"
        << quote(oid_to_string(git_commit_id(local)));
    out << ",\"upstreamOid\":"
        << quote(oid_to_string(git_commit_id(upstream)));
    out << ",\"ahead\":" << ahead;
    out << ",\"behind\":" << behind;
    out << "}";

    git_commit_free(upstream);
    git_commit_free(local);
    git_repository_free(repository);
    return out.str();
}

std::string repository_state(
    const std::string& repository_path
) {
    git_repository* repository = open_repository(repository_path);
    const int state = git_repository_state(repository);
    git_repository_free(repository);

    switch (state) {
        case GIT_REPOSITORY_STATE_NONE:
            return "none";
        case GIT_REPOSITORY_STATE_MERGE:
            return "merge";
        case GIT_REPOSITORY_STATE_REBASE:
        case GIT_REPOSITORY_STATE_REBASE_INTERACTIVE:
        case GIT_REPOSITORY_STATE_REBASE_MERGE:
            return "rebase";
        case GIT_REPOSITORY_STATE_CHERRYPICK:
        case GIT_REPOSITORY_STATE_CHERRYPICK_SEQUENCE:
            return "cherry_pick";
        case GIT_REPOSITORY_STATE_REVERT:
        case GIT_REPOSITORY_STATE_REVERT_SEQUENCE:
            return "revert";
        default:
            return "other";
    }
}

void create_branch(
    const std::string& repository_path,
    const std::string& name,
    const std::string& start_point
) {
    if (name.empty()) {
        throw GitError(GIT_EINVALID, 0, "Branch name is required");
    }

    git_repository* repository = open_repository(repository_path);
    git_commit* target = nullptr;

    try {
        target = resolve_commit(repository, start_point);
    } catch (...) {
        git_repository_free(repository);
        throw;
    }

    git_reference* branch = nullptr;
    const int rc = git_branch_create(
        &branch,
        repository,
        name.c_str(),
        target,
        0
    );

    git_commit_free(target);
    if (branch != nullptr) git_reference_free(branch);
    git_repository_free(repository);
    check(rc, "Create branch");
}

void checkout(
    const std::string& repository_path,
    const std::string& ref
) {
    if (ref.empty()) {
        throw GitError(GIT_EINVALID, 0, "Checkout ref is required");
    }

    git_repository* repository = open_repository(repository_path);

    git_checkout_options options = GIT_CHECKOUT_OPTIONS_INIT;
    options.checkout_strategy =
        GIT_CHECKOUT_SAFE | GIT_CHECKOUT_RECREATE_MISSING;

    git_reference* branch = nullptr;
    int rc = git_branch_lookup(
        &branch,
        repository,
        ref.c_str(),
        GIT_BRANCH_LOCAL
    );

    if (rc == 0) {
        const char* full_name = git_reference_name(branch);
        if (full_name == nullptr) {
            git_reference_free(branch);
            git_repository_free(repository);
            throw GitError(GIT_ERROR, 0, "Branch has no reference name");
        }

        git_object* target = nullptr;
        const int peel_rc = git_reference_peel(
            &target,
            branch,
            GIT_OBJECT_COMMIT
        );
        if (peel_rc < 0) {
            git_reference_free(branch);
            git_repository_free(repository);
            throw_git_error(peel_rc, "Resolve branch target");
        }

        rc = git_checkout_tree(repository, target, &options);
        git_object_free(target);

        if (rc == 0) {
            rc = git_repository_set_head(repository, full_name);
        }

        git_reference_free(branch);
        git_repository_free(repository);
        check(rc, "Checkout branch");
        return;
    }

    if (rc != GIT_ENOTFOUND) {
        git_repository_free(repository);
        throw_git_error(rc, "Look up branch");
    }

    git_object* object = nullptr;
    check(
        git_revparse_single(&object, repository, ref.c_str()),
        "Resolve checkout revision"
    );

    rc = git_checkout_tree(repository, object, &options);
    if (rc == 0) {
        const git_oid* oid = git_object_id(object);
        rc = git_repository_set_head_detached(repository, oid);
    }

    git_object_free(object);
    git_repository_free(repository);
    check(rc, "Checkout detached revision");
}

void fetch(
    const std::string& repository_path,
    const std::string& remote_name,
    const Credentials& credentials
) {
    git_repository* repository = open_repository(repository_path);
    git_remote* remote = nullptr;

    const std::string name = remote_name.empty() ? "origin" : remote_name;
    const int lookup_rc = git_remote_lookup(
        &remote,
        repository,
        name.c_str()
    );
    if (lookup_rc < 0) {
        git_repository_free(repository);
        throw_git_error(lookup_rc, "Open remote");
    }

    git_fetch_options options = GIT_FETCH_OPTIONS_INIT;
    options.callbacks = remote_callbacks(&credentials);
    options.prune = GIT_FETCH_PRUNE_UNSPECIFIED;
    options.update_fetchhead = 1;

    const int rc = git_remote_fetch(
        remote,
        nullptr,
        &options,
        "Nexora Git fetch"
    );

    git_remote_free(remote);
    git_repository_free(repository);
    check(rc, "Fetch remote");
}

std::string pull(
    const std::string& repository_path,
    const std::string& remote_name,
    const Author& author,
    const Credentials& credentials
) {
    return pull_with_strategy(
        repository_path,
        remote_name,
        "merge",
        author,
        credentials
    );
}

std::string pull_with_strategy(
    const std::string& repository_path,
    const std::string& remote_name,
    const std::string& strategy,
    const Author& author,
    const Credentials& credentials
) {
    const std::string remote =
        remote_name.empty() ? "origin" : remote_name;

    if (strategy != "merge" &&
        strategy != "ff_only" &&
        strategy != "rebase") {
        throw GitError(
            GIT_EINVALID,
            0,
            "Unsupported pull strategy"
        );
    }

    fetch(repository_path, remote, credentials);

    git_repository* repository = open_repository(repository_path);
    git_reference* head = nullptr;
    check(git_repository_head(&head, repository), "Read local branch");

    if (!git_reference_is_branch(head)) {
        git_reference_free(head);
        git_repository_free(repository);
        throw GitError(
            GIT_EINVALID,
            0,
            "Pull requires a checked-out local branch"
        );
    }

    git_reference* upstream = nullptr;
    try {
        upstream = resolve_pull_upstream(
            repository,
            head,
            remote
        );
    } catch (...) {
        git_reference_free(head);
        git_repository_free(repository);
        throw;
    }

    git_annotated_commit* target = nullptr;
    const int annotated_rc = git_annotated_commit_from_ref(
        &target,
        repository,
        upstream
    );

    const std::string branch_name =
        git_reference_shorthand(head) != nullptr
            ? git_reference_shorthand(head)
            : "branch";
    const std::string upstream_name =
        git_reference_shorthand(upstream) != nullptr
            ? git_reference_shorthand(upstream)
            : remote;

    if (annotated_rc < 0) {
        git_reference_free(upstream);
        git_reference_free(head);
        git_repository_free(repository);
        throw_git_error(annotated_rc, "Resolve fetched commit");
    }

    if (strategy == "ff_only") {
        std::string result;
        try {
            result = fast_forward_only(
                repository,
                head,
                target
            );
        } catch (...) {
            git_annotated_commit_free(target);
            git_reference_free(upstream);
            git_reference_free(head);
            git_repository_free(repository);
            throw;
        }

        git_annotated_commit_free(target);
        git_reference_free(upstream);
        git_reference_free(head);
        git_repository_free(repository);
        return result;
    }

    if (strategy == "rebase") {
        git_merge_analysis_t analysis = GIT_MERGE_ANALYSIS_NONE;
        git_merge_preference_t preference = GIT_MERGE_PREFERENCE_NONE;
        const git_annotated_commit* heads[] = {target};

        const int analysis_rc = git_merge_analysis(
            &analysis,
            &preference,
            repository,
            heads,
            1
        );
        if (analysis_rc < 0) {
            git_annotated_commit_free(target);
            git_reference_free(upstream);
            git_reference_free(head);
            git_repository_free(repository);
            throw_git_error(analysis_rc, "Analyze rebase pull");
        }

        if ((analysis & GIT_MERGE_ANALYSIS_UP_TO_DATE) != 0) {
            git_annotated_commit_free(target);
            git_reference_free(upstream);
            git_reference_free(head);
            git_repository_free(repository);
            return merge_result_json(
                "up_to_date",
                "",
                "[]"
            );
        }

        if ((analysis & GIT_MERGE_ANALYSIS_FASTFORWARD) != 0 ||
            (analysis & GIT_MERGE_ANALYSIS_UNBORN) != 0) {
            std::string result;
            try {
                result = fast_forward(
                    repository,
                    head,
                    target
                );
            } catch (...) {
                git_annotated_commit_free(target);
                git_reference_free(upstream);
                git_reference_free(head);
                git_repository_free(repository);
                throw;
            }

            git_annotated_commit_free(target);
            git_reference_free(upstream);
            git_reference_free(head);
            git_repository_free(repository);
            return result;
        }

        git_rebase_options options = GIT_REBASE_OPTIONS_INIT;
        options.checkout_options.checkout_strategy =
            GIT_CHECKOUT_SAFE |
            GIT_CHECKOUT_RECREATE_MISSING |
            GIT_CHECKOUT_ALLOW_CONFLICTS |
            GIT_CHECKOUT_CONFLICT_STYLE_MERGE;

        git_rebase* rebase = nullptr;
        const int rebase_rc = git_rebase_init(
            &rebase,
            repository,
            nullptr,
            target,
            nullptr,
            &options
        );

        git_annotated_commit_free(target);
        git_reference_free(upstream);
        git_reference_free(head);

        if (rebase_rc < 0) {
            git_repository_free(repository);
            throw_git_error(rebase_rc, "Start rebase pull");
        }

        std::string result;
        try {
            result = advance_rebase(
                repository,
                rebase,
                author,
                false
            );
        } catch (...) {
            git_repository_free(repository);
            throw;
        }

        git_repository_free(repository);
        return result;
    }

    git_reference_free(upstream);
    git_reference_free(head);

    std::string result;
    try {
        result = merge_annotated(
            repository,
            target,
            author,
            "Merge " + upstream_name + " into " + branch_name
        );
    } catch (...) {
        git_annotated_commit_free(target);
        git_repository_free(repository);
        throw;
    }

    git_annotated_commit_free(target);
    git_repository_free(repository);
    return result;
}

std::string continue_merge(
    const std::string& repository_path,
    const Author& author
) {
    git_repository* repository = open_repository(repository_path);

    if (git_repository_state(repository) !=
        GIT_REPOSITORY_STATE_MERGE) {
        git_repository_free(repository);
        throw GitError(
            GIT_EINVALID,
            0,
            "No merge is in progress"
        );
    }

    if (repository_has_conflicts(repository)) {
        const std::string payload = conflicts_json(repository);
        git_repository_free(repository);
        return merge_result_json(
            "conflicts",
            "",
            payload
        );
    }

    std::vector<git_oid> merge_heads;
    const int merge_heads_rc =
        git_repository_mergehead_foreach(
            repository,
            collect_merge_head,
            &merge_heads
        );
    if (merge_heads_rc < 0) {
        git_repository_free(repository);
        throw_git_error(
            merge_heads_rc,
            "Read merge heads"
        );
    }
    if (merge_heads.size() != 1) {
        git_repository_free(repository);
        throw GitError(
            GIT_EINVALID,
            0,
            "Nexora Git expected exactly one merge head"
        );
    }

    const git_oid incoming_oid = merge_heads.front();

    git_reference* head_reference = nullptr;
    const int head_rc = git_repository_head(
        &head_reference,
        repository
    );
    if (head_rc < 0) {
        git_repository_free(repository);
        throw_git_error(
            head_rc,
            "Read local merge parent"
        );
    }

    git_commit* local_commit = nullptr;
    const int local_rc = git_reference_peel(
        reinterpret_cast<git_object**>(&local_commit),
        head_reference,
        GIT_OBJECT_COMMIT
    );
    git_reference_free(head_reference);
    if (local_rc < 0) {
        git_repository_free(repository);
        throw_git_error(
            local_rc,
            "Resolve local merge parent"
        );
    }

    git_commit* incoming_commit = nullptr;
    const int incoming_rc = git_commit_lookup(
        &incoming_commit,
        repository,
        &incoming_oid
    );
    if (incoming_rc < 0) {
        git_commit_free(local_commit);
        git_repository_free(repository);
        throw_git_error(
            incoming_rc,
            "Resolve incoming merge parent"
        );
    }

    git_index* index = nullptr;
    const int index_rc = git_repository_index(
        &index,
        repository
    );
    if (index_rc < 0) {
        git_commit_free(incoming_commit);
        git_commit_free(local_commit);
        git_repository_free(repository);
        throw_git_error(
            index_rc,
            "Open resolved merge index"
        );
    }

    git_oid tree_oid{};
    const int tree_write_rc = git_index_write_tree(
        &tree_oid,
        index
    );
    if (tree_write_rc < 0) {
        git_index_free(index);
        git_commit_free(incoming_commit);
        git_commit_free(local_commit);
        git_repository_free(repository);
        throw_git_error(
            tree_write_rc,
            "Write resolved merge tree"
        );
    }

    const int index_write_rc = git_index_write(index);
    if (index_write_rc < 0) {
        git_index_free(index);
        git_commit_free(incoming_commit);
        git_commit_free(local_commit);
        git_repository_free(repository);
        throw_git_error(
            index_write_rc,
            "Write resolved merge index"
        );
    }

    git_tree* tree = nullptr;
    const int tree_rc = git_tree_lookup(
        &tree,
        repository,
        &tree_oid
    );
    if (tree_rc < 0) {
        git_index_free(index);
        git_commit_free(incoming_commit);
        git_commit_free(local_commit);
        git_repository_free(repository);
        throw_git_error(
            tree_rc,
            "Open resolved merge tree"
        );
    }

    git_buf merge_message = GIT_BUF_INIT;
    std::string message = "Merge conflict resolution";
    if (git_repository_message(
            &merge_message,
            repository
        ) == 0 &&
        merge_message.ptr != nullptr &&
        merge_message.size > 0) {
        message.assign(
            merge_message.ptr,
            merge_message.size
        );
        while (!message.empty() &&
               (message.back() == '\n' ||
                message.back() == '\r')) {
            message.pop_back();
        }
    }

    git_signature* signature = make_signature(author);
    const git_commit* parents[] = {
        local_commit,
        incoming_commit,
    };
    git_oid commit_oid{};

    const int commit_rc = git_commit_create(
        &commit_oid,
        repository,
        "HEAD",
        signature,
        signature,
        nullptr,
        message.c_str(),
        tree,
        2,
        parents
    );

    git_signature_free(signature);
    git_buf_dispose(&merge_message);
    git_tree_free(tree);
    git_index_free(index);
    git_commit_free(incoming_commit);
    git_commit_free(local_commit);

    if (commit_rc < 0) {
        git_repository_free(repository);
        throw_git_error(
            commit_rc,
            "Create resolved merge commit"
        );
    }

    const int cleanup_rc =
        git_repository_state_cleanup(repository);
    git_repository_free(repository);
    check(cleanup_rc, "Clean merge state");

    return merge_result_json(
        "merged",
        oid_to_string(&commit_oid),
        "[]"
    );
}

std::string continue_rebase(
    const std::string& repository_path,
    const Author& author
) {
    git_repository* repository = open_repository(repository_path);

    const int state = git_repository_state(repository);
    if (state != GIT_REPOSITORY_STATE_REBASE &&
        state != GIT_REPOSITORY_STATE_REBASE_INTERACTIVE &&
        state != GIT_REPOSITORY_STATE_REBASE_MERGE) {
        git_repository_free(repository);
        throw GitError(
            GIT_EINVALID,
            0,
            "No rebase is in progress"
        );
    }

    git_rebase_options options = GIT_REBASE_OPTIONS_INIT;
    options.checkout_options.checkout_strategy =
        GIT_CHECKOUT_SAFE |
        GIT_CHECKOUT_RECREATE_MISSING |
        GIT_CHECKOUT_ALLOW_CONFLICTS |
        GIT_CHECKOUT_CONFLICT_STYLE_MERGE;

    git_rebase* rebase = nullptr;
    const int open_rc = git_rebase_open(
        &rebase,
        repository,
        &options
    );
    if (open_rc < 0) {
        git_repository_free(repository);
        throw_git_error(open_rc, "Open in-progress rebase");
    }

    std::string result;
    try {
        result = advance_rebase(
            repository,
            rebase,
            author,
            true
        );
    } catch (...) {
        git_repository_free(repository);
        throw;
    }

    git_repository_free(repository);
    return result;
}

void abort_rebase(
    const std::string& repository_path
) {
    git_repository* repository = open_repository(repository_path);
    git_rebase_options options = GIT_REBASE_OPTIONS_INIT;
    git_rebase* rebase = nullptr;

    const int open_rc = git_rebase_open(
        &rebase,
        repository,
        &options
    );
    if (open_rc < 0) {
        git_repository_free(repository);
        throw_git_error(open_rc, "Open rebase to abort");
    }

    const int abort_rc = git_rebase_abort(rebase);
    git_rebase_free(rebase);
    git_repository_free(repository);
    check(abort_rc, "Abort rebase");
}

std::string push(
    const std::string& repository_path,
    const std::string& remote_name,
    const std::string& requested_refspec,
    const Credentials& credentials
) {
    git_repository* repository = open_repository(repository_path);
    git_remote* remote = nullptr;

    const std::string name = remote_name.empty() ? "origin" : remote_name;
    const int lookup_rc = git_remote_lookup(
        &remote,
        repository,
        name.c_str()
    );
    if (lookup_rc < 0) {
        git_repository_free(repository);
        throw_git_error(lookup_rc, "Open push remote");
    }

    std::string refspec = requested_refspec;
    if (refspec.empty()) {
        const std::string branch = current_branch_name(repository);
        if (branch.empty()) {
            git_remote_free(remote);
            git_repository_free(repository);
            throw GitError(
                GIT_EINVALID,
                0,
                "Push requires a branch or explicit refspec"
            );
        }
        refspec =
            "refs/heads/" + branch +
            ":refs/heads/" + branch;
    }

    char* refspec_raw = const_cast<char*>(refspec.c_str());
    git_strarray refspecs{};
    refspecs.strings = &refspec_raw;
    refspecs.count = 1;

    git_push_options options = GIT_PUSH_OPTIONS_INIT;
    options.callbacks = remote_callbacks(&credentials);

    const int rc = git_remote_push(
        remote,
        &refspecs,
        &options
    );

    git_remote_free(remote);
    git_repository_free(repository);
    check(rc, "Push remote");

    std::ostringstream out;
    out << "{";
    out << "\"remote\":" << quote(name);
    out << ",\"refspec\":" << quote(refspec);
    out << ",\"forceWithLease\":false";
    out << "}";
    return out.str();
}


std::string push_force_with_lease(
    const std::string& repository_path,
    const std::string& remote_name,
    const std::string& requested_refspec,
    const std::string& expected_remote_oid,
    const Credentials& credentials
) {
    if (requested_refspec.empty() ||
        expected_remote_oid.empty()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Force-with-lease requires an explicit refspec and expected remote OID"
        );
    }

    const size_t colon = requested_refspec.find(':');
    if (colon == std::string::npos ||
        colon + 1 >= requested_refspec.size()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Force-with-lease requires an explicit destination ref"
        );
    }

    const std::string destination =
        requested_refspec.substr(colon + 1);
    if (destination.rfind("refs/heads/", 0) != 0) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Force-with-lease is restricted to branch refs"
        );
    }

    git_oid expected{};
    if (git_oid_fromstr(&expected, expected_remote_oid.c_str()) < 0) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Expected remote OID is invalid"
        );
    }

    git_repository* repository = open_repository(repository_path);
    git_remote* remote = nullptr;
    const std::string name =
        remote_name.empty() ? "origin" : remote_name;

    const int lookup_rc = git_remote_lookup(
        &remote,
        repository,
        name.c_str()
    );
    if (lookup_rc < 0) {
        git_repository_free(repository);
        throw_git_error(lookup_rc, "Open lease push remote");
    }

    git_remote_connect_options connect_options =
        GIT_REMOTE_CONNECT_OPTIONS_INIT;
    connect_options.callbacks =
        remote_callbacks(&credentials);

    const int connect_rc = git_remote_connect_ext(
        remote,
        GIT_DIRECTION_PUSH,
        &connect_options
    );
    if (connect_rc < 0) {
        git_remote_free(remote);
        git_repository_free(repository);
        throw_git_error(connect_rc, "Connect lease push remote");
    }

    const git_remote_head** heads = nullptr;
    size_t heads_len = 0;
    const int ls_rc = git_remote_ls(
        &heads,
        &heads_len,
        remote
    );
    if (ls_rc < 0) {
        git_remote_disconnect(remote);
        git_remote_free(remote);
        git_repository_free(repository);
        throw_git_error(ls_rc, "Inspect remote lease");
    }

    bool found = false;
    git_oid actual{};
    for (size_t index = 0; index < heads_len; ++index) {
        const git_remote_head* head = heads[index];
        if (head != nullptr &&
            head->name != nullptr &&
            destination == head->name) {
            actual = head->oid;
            found = true;
            break;
        }
    }

    if (!found || git_oid_cmp(&actual, &expected) != 0) {
        git_remote_disconnect(remote);
        git_remote_free(remote);
        git_repository_free(repository);
        throw GitError(
            GIT_ENONFASTFORWARD,
            0,
            "Force-with-lease rejected because the remote branch changed since the last verified fetch"
        );
    }

    std::string forced_refspec = requested_refspec;
    if (forced_refspec.front() != '+') {
        forced_refspec.insert(
            forced_refspec.begin(),
            '+'
        );
    }

    char* refspec_raw =
        const_cast<char*>(forced_refspec.c_str());
    git_strarray refspecs{};
    refspecs.strings = &refspec_raw;
    refspecs.count = 1;

    git_push_options options = GIT_PUSH_OPTIONS_INIT;
    options.callbacks = remote_callbacks(&credentials);

    const int push_rc = git_remote_push(
        remote,
        &refspecs,
        &options
    );

    git_remote_disconnect(remote);
    git_remote_free(remote);
    git_repository_free(repository);
    check(push_rc, "Force-with-lease push");

    std::ostringstream out;
    out << "{";
    out << "\"remote\":" << quote(name);
    out << ",\"refspec\":" << quote(forced_refspec);
    out << ",\"forceWithLease\":true";
    out << "}";
    return out.str();
}

std::string diff(
    const std::string& repository_path,
    const std::string& mode,
    const std::string& relative_path
) {
    git_repository* repository = open_repository(repository_path);
    git_diff* result = nullptr;

    git_diff_options options = GIT_DIFF_OPTIONS_INIT;
    options.flags =
        GIT_DIFF_INCLUDE_UNTRACKED |
        GIT_DIFF_RECURSE_UNTRACKED_DIRS |
        GIT_DIFF_INCLUDE_TYPECHANGE;

    char* path_raw = nullptr;
    git_strarray pathspec{};
    if (!relative_path.empty()) {
        path_raw = const_cast<char*>(relative_path.c_str());
        pathspec.strings = &path_raw;
        pathspec.count = 1;
        options.pathspec = pathspec;
        options.flags |= GIT_DIFF_DISABLE_PATHSPEC_MATCH;
    }

    git_tree* tree = nullptr;
    git_index* index = nullptr;

    try {
        if (mode == "staged") {
            tree = head_tree(repository);
            check(
                git_repository_index(&index, repository),
                "Open repository index"
            );
            check(
                git_diff_tree_to_index(
                    &result,
                    repository,
                    tree,
                    index,
                    &options
                ),
                "Create staged diff"
            );
        } else if (mode == "unstaged") {
            check(
                git_repository_index(&index, repository),
                "Open repository index"
            );
            check(
                git_diff_index_to_workdir(
                    &result,
                    repository,
                    index,
                    &options
                ),
                "Create unstaged diff"
            );
        } else {
            tree = head_tree(repository);
            check(
                git_diff_tree_to_workdir_with_index(
                    &result,
                    repository,
                    tree,
                    &options
                ),
                "Create working tree diff"
            );
        }
    } catch (...) {
        if (index != nullptr) git_index_free(index);
        if (tree != nullptr) git_tree_free(tree);
        git_repository_free(repository);
        throw;
    }

    git_buf buffer = GIT_BUF_INIT;
    check(
        git_diff_to_buf(&buffer, result, GIT_DIFF_FORMAT_PATCH),
        "Render diff"
    );

    git_diff_stats* stats = nullptr;
    check(git_diff_get_stats(&stats, result), "Read diff stats");

    std::ostringstream out;
    out << "{";
    out << "\"patch\":"
        << quote(buffer.ptr != nullptr ? buffer.ptr : "");
    out << ",\"filesChanged\":"
        << git_diff_stats_files_changed(stats);
    out << ",\"insertions\":"
        << git_diff_stats_insertions(stats);
    out << ",\"deletions\":"
        << git_diff_stats_deletions(stats);
    out << "}";

    git_diff_stats_free(stats);
    git_buf_dispose(&buffer);
    git_diff_free(result);
    if (index != nullptr) git_index_free(index);
    if (tree != nullptr) git_tree_free(tree);
    git_repository_free(repository);
    return out.str();
}

std::string merge(
    const std::string& repository_path,
    const std::string& ref,
    const Author& author
) {
    if (ref.empty()) {
        throw GitError(GIT_EINVALID, 0, "Merge ref is required");
    }

    git_repository* repository = open_repository(repository_path);
    git_object* object = nullptr;

    const int resolve_rc = git_revparse_single(
        &object,
        repository,
        ref.c_str()
    );
    if (resolve_rc < 0) {
        git_repository_free(repository);
        throw_git_error(resolve_rc, "Resolve merge ref");
    }

    git_annotated_commit* target = nullptr;
    const int annotated_rc = git_annotated_commit_lookup(
        &target,
        repository,
        git_object_id(object)
    );
    git_object_free(object);

    if (annotated_rc < 0) {
        git_repository_free(repository);
        throw_git_error(annotated_rc, "Create merge target");
    }

    std::string result;
    try {
        result = merge_annotated(
            repository,
            target,
            author,
            "Merge " + ref
        );
    } catch (...) {
        git_annotated_commit_free(target);
        git_repository_free(repository);
        throw;
    }

    git_annotated_commit_free(target);
    git_repository_free(repository);
    return result;
}

std::string conflicts(const std::string& repository_path) {
    git_repository* repository = open_repository(repository_path);
    const std::string result = conflicts_json(repository);
    git_repository_free(repository);
    return result;
}

void resolve_conflict(
    const std::string& repository_path,
    const std::string& path,
    const std::string& resolution
) {
    if (path.empty()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Conflict path is required"
        );
    }
    if (resolution != "ours" &&
        resolution != "theirs") {
        throw GitError(
            GIT_EINVALID,
            0,
            "Conflict resolution must be ours or theirs"
        );
    }

    git_repository* repository = open_repository(repository_path);
    git_index* index = nullptr;
    const int index_rc = git_repository_index(
        &index,
        repository
    );
    if (index_rc < 0) {
        git_repository_free(repository);
        throw_git_error(index_rc, "Open conflict index");
    }

    const git_index_entry* ancestor = nullptr;
    const git_index_entry* ours = nullptr;
    const git_index_entry* theirs = nullptr;
    const int conflict_rc = git_index_conflict_get(
        &ancestor,
        &ours,
        &theirs,
        index,
        path.c_str()
    );
    if (conflict_rc < 0) {
        git_index_free(index);
        git_repository_free(repository);
        throw_git_error(conflict_rc, "Read conflict entry");
    }

    const git_index_entry* selected =
        resolution == "ours" ? ours : theirs;

    git_index_entry resolved{};
    bool keep_path = selected != nullptr;
    if (keep_path) {
        resolved = *selected;
        resolved.path = path.c_str();
        GIT_INDEX_ENTRY_STAGE_SET(&resolved, 0);
    }

    const int remove_conflict_rc =
        git_index_conflict_remove(
            index,
            path.c_str()
        );
    if (remove_conflict_rc < 0) {
        git_index_free(index);
        git_repository_free(repository);
        throw_git_error(
            remove_conflict_rc,
            "Remove conflict entries"
        );
    }

    if (keep_path) {
        const int add_rc = git_index_add(
            index,
            &resolved
        );
        if (add_rc < 0) {
            git_index_free(index);
            git_repository_free(repository);
            throw_git_error(
                add_rc,
                "Stage selected conflict side"
            );
        }
    } else {
        const int remove_rc = git_index_remove_bypath(
            index,
            path.c_str()
        );
        if (remove_rc < 0 &&
            remove_rc != GIT_ENOTFOUND) {
            git_index_free(index);
            git_repository_free(repository);
            throw_git_error(
                remove_rc,
                "Stage conflict deletion"
            );
        }
    }

    const int write_rc = git_index_write(index);
    if (write_rc < 0) {
        git_index_free(index);
        git_repository_free(repository);
        throw_git_error(
            write_rc,
            "Write resolved conflict index"
        );
    }

    if (keep_path) {
        char* path_raw =
            const_cast<char*>(path.c_str());
        git_strarray pathspec{};
        pathspec.strings = &path_raw;
        pathspec.count = 1;

        git_checkout_options options =
            GIT_CHECKOUT_OPTIONS_INIT;
        options.checkout_strategy =
            GIT_CHECKOUT_FORCE;
        options.paths = pathspec;

        const int checkout_rc = git_checkout_index(
            repository,
            index,
            &options
        );
        if (checkout_rc < 0) {
            git_index_free(index);
            git_repository_free(repository);
            throw_git_error(
                checkout_rc,
                "Checkout selected conflict side"
            );
        }
    } else {
        const char* workdir =
            git_repository_workdir(repository);
        if (workdir != nullptr) {
            const std::string full_path =
                std::string(workdir) + path;
            std::remove(full_path.c_str());
        }
    }

    git_index_free(index);
    git_repository_free(repository);
}

std::string history(
    const std::string& repository_path,
    const std::string& relative_path,
    int requested_limit
) {
    git_repository* repository = open_repository(repository_path);
    git_revwalk* walk = nullptr;
    check(git_revwalk_new(&walk, repository), "Create history walker");

    git_revwalk_sorting(
        walk,
        GIT_SORT_TOPOLOGICAL | GIT_SORT_TIME
    );

    const int push_rc = git_revwalk_push_head(walk);
    if (push_rc == GIT_EUNBORNBRANCH || push_rc == GIT_ENOTFOUND) {
        git_revwalk_free(walk);
        git_repository_free(repository);
        return "[]";
    }
    if (push_rc < 0) {
        git_revwalk_free(walk);
        git_repository_free(repository);
        throw_git_error(push_rc, "Start history at HEAD");
    }

    const int limit = std::max(1, std::min(requested_limit, 200));
    std::ostringstream out;
    out << "[";
    bool first = true;
    int emitted = 0;

    git_oid oid{};
    while (emitted < limit) {
        const int next_rc = git_revwalk_next(&oid, walk);
        if (next_rc == GIT_ITEROVER) break;
        if (next_rc < 0) {
            git_revwalk_free(walk);
            git_repository_free(repository);
            throw_git_error(next_rc, "Walk commit history");
        }

        git_commit* commit = nullptr;
        check(
            git_commit_lookup(&commit, repository, &oid),
            "Open history commit"
        );

        bool include = relative_path.empty();

        if (!include) {
            git_tree* current_tree = nullptr;
            check(
                git_commit_tree(&current_tree, commit),
                "Read history commit tree"
            );

            char* path_raw =
                const_cast<char*>(relative_path.c_str());
            git_strarray pathspec{};
            pathspec.strings = &path_raw;
            pathspec.count = 1;

            git_diff_options options = GIT_DIFF_OPTIONS_INIT;
            options.pathspec = pathspec;
            options.flags =
                GIT_DIFF_INCLUDE_TYPECHANGE;

            const size_t parent_count =
                git_commit_parentcount(commit);

            if (parent_count == 0) {
                git_diff* commit_diff = nullptr;
                const int diff_rc = git_diff_tree_to_tree(
                    &commit_diff,
                    repository,
                    nullptr,
                    current_tree,
                    &options
                );
                if (diff_rc < 0) {
                    git_tree_free(current_tree);
                    git_commit_free(commit);
                    git_revwalk_free(walk);
                    git_repository_free(repository);
                    throw_git_error(
                        diff_rc,
                        "Compare initial commit path"
                    );
                }

                include =
                    git_diff_num_deltas(commit_diff) > 0;
                git_diff_free(commit_diff);
            } else {
                for (
                    size_t parent_index = 0;
                    parent_index < parent_count && !include;
                    ++parent_index
                ) {
                    git_commit* parent = nullptr;
                    check(
                        git_commit_parent(
                            &parent,
                            commit,
                            static_cast<unsigned int>(
                                parent_index
                            )
                        ),
                        "Read history parent"
                    );

                    git_tree* parent_tree = nullptr;
                    const int parent_tree_rc =
                        git_commit_tree(
                            &parent_tree,
                            parent
                        );
                    git_commit_free(parent);

                    if (parent_tree_rc < 0) {
                        git_tree_free(current_tree);
                        git_commit_free(commit);
                        git_revwalk_free(walk);
                        git_repository_free(repository);
                        throw_git_error(
                            parent_tree_rc,
                            "Read history parent tree"
                        );
                    }

                    git_diff* commit_diff = nullptr;
                    const int diff_rc = git_diff_tree_to_tree(
                        &commit_diff,
                        repository,
                        parent_tree,
                        current_tree,
                        &options
                    );
                    git_tree_free(parent_tree);

                    if (diff_rc < 0) {
                        git_tree_free(current_tree);
                        git_commit_free(commit);
                        git_revwalk_free(walk);
                        git_repository_free(repository);
                        throw_git_error(
                            diff_rc,
                            "Compare history commit path"
                        );
                    }

                    include =
                        git_diff_num_deltas(commit_diff) > 0;
                    git_diff_free(commit_diff);
                }
            }

            git_tree_free(current_tree);
        }

        if (!include) {
            git_commit_free(commit);
            continue;
        }

        const git_signature* author = git_commit_author(commit);
        const char* summary = git_commit_summary(commit);
        const char* message = git_commit_message(commit);

        const std::string oid_string = oid_to_string(&oid);
        const std::string short_oid =
            oid_string.substr(
                0,
                std::min<size_t>(7, oid_string.size())
            );

        if (!first) out << ",";
        first = false;

        out << "{";
        out << "\"oid\":" << quote(oid_string);
        out << ",\"shortOid\":" << quote(short_oid);
        out << ",\"summary\":"
            << quote(summary != nullptr ? summary : "");
        out << ",\"message\":"
            << quote(message != nullptr ? message : "");
        out << ",\"authorName\":"
            << quote(
                author != nullptr && author->name != nullptr
                    ? author->name
                    : ""
            );
        out << ",\"authorEmail\":"
            << quote(
                author != nullptr && author->email != nullptr
                    ? author->email
                    : ""
            );
        out << ",\"timestampSeconds\":"
            << git_commit_time(commit);
        out << ",\"timezoneOffsetMinutes\":"
            << git_commit_time_offset(commit);
        out << ",\"parentCount\":"
            << git_commit_parentcount(commit);
        out << "}";

        emitted += 1;
        git_commit_free(commit);
    }

    out << "]";
    git_revwalk_free(walk);
    git_repository_free(repository);
    return out.str();
}

std::string blame(
    const std::string& repository_path,
    const std::string& relative_path
) {
    if (relative_path.empty()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Blame path is required"
        );
    }

    git_repository* repository = open_repository(repository_path);
    git_blame_options options = GIT_BLAME_OPTIONS_INIT;

    git_blame* blame_result = nullptr;
    const int blame_rc = git_blame_file(
        &blame_result,
        repository,
        relative_path.c_str(),
        &options
    );
    if (blame_rc < 0) {
        git_repository_free(repository);
        throw_git_error(blame_rc, "Blame file");
    }

    std::ostringstream out;
    out << "[";

    const size_t count = git_blame_get_hunk_count(blame_result);
    for (size_t index = 0; index < count; ++index) {
        const git_blame_hunk* hunk =
            git_blame_get_hunk_byindex(
                blame_result,
                static_cast<uint32_t>(index)
            );

        if (hunk == nullptr) continue;
        if (index > 0) out << ",";

        out << "{";
        out << "\"startLine\":"
            << hunk->final_start_line_number;
        out << ",\"lineCount\":"
            << hunk->lines_in_hunk;
        out << ",\"finalCommitOid\":"
            << quote(oid_to_string(&hunk->final_commit_id));
        out << ",\"originalCommitOid\":"
            << quote(oid_to_string(&hunk->orig_commit_id));
        out << ",\"originalStartLine\":"
            << hunk->orig_start_line_number;
        out << ",\"originalPath\":"
            << quote(hunk->orig_path != nullptr
                ? hunk->orig_path
                : relative_path);
        out << ",\"authorName\":"
            << quote(
                hunk->final_signature != nullptr &&
                hunk->final_signature->name != nullptr
                    ? hunk->final_signature->name
                    : ""
            );
        out << ",\"authorEmail\":"
            << quote(
                hunk->final_signature != nullptr &&
                hunk->final_signature->email != nullptr
                    ? hunk->final_signature->email
                    : ""
            );
        out << ",\"timestampSeconds\":"
            << (
                hunk->final_signature != nullptr
                    ? hunk->final_signature->when.time
                    : 0
            );
        out << ",\"timezoneOffsetMinutes\":"
            << (
                hunk->final_signature != nullptr
                    ? hunk->final_signature->when.offset
                    : 0
            );
        out << ",\"boundary\":"
            << (hunk->boundary != 0 ? "true" : "false");
        out << "}";
    }

    out << "]";

    git_blame_free(blame_result);
    git_repository_free(repository);
    return out.str();
}

}  // namespace nexora::git
