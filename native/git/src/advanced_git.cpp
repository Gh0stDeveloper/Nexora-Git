#include "nexora/git_core.h"

#include <git2.h>

#include <algorithm>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

namespace nexora::git {
namespace {

[[noreturn]] void throw_advanced_error(
    int code,
    const std::string& operation
) {
    const git_error* error = git_error_last();
    const int klass = error != nullptr ? error->klass : 0;
    const std::string detail =
        error != nullptr && error->message != nullptr
            ? error->message
            : "Unknown libgit2 error";
    throw GitError(code, klass, operation + ": " + detail);
}

void check_advanced(
    int code,
    const std::string& operation
) {
    if (code < 0) {
        throw_advanced_error(code, operation);
    }
}

git_repository* open_advanced_repository(
    const std::string& path
) {
    git_repository* repository = nullptr;
    check_advanced(
        git_repository_open(&repository, path.c_str()),
        "Open repository"
    );
    return repository;
}

std::string escape_json(const std::string& value) {
    std::ostringstream out;
    for (const unsigned char ch : value) {
        switch (ch) {
            case '"': out << "\\\""; break;
            case '\\': out << "\\\\"; break;
            case '\b': out << "\\b"; break;
            case '\f': out << "\\f"; break;
            case '\n': out << "\\n"; break;
            case '\r': out << "\\r"; break;
            case '\t': out << "\\t"; break;
            default:
                if (ch < 0x20) {
                    const char* hex = "0123456789abcdef";
                    out << "\\u00"
                        << hex[(ch >> 4) & 0x0f]
                        << hex[ch & 0x0f];
                } else {
                    out << static_cast<char>(ch);
                }
        }
    }
    return out.str();
}

std::string json_quote(const std::string& value) {
    return "\"" + escape_json(value) + "\"";
}

std::string oid_string(const git_oid* oid) {
    if (oid == nullptr) return "";
    char buffer[GIT_OID_SHA1_HEXSIZE + 1]{};
    git_oid_tostr(buffer, sizeof(buffer), oid);
    return buffer;
}

git_signature* advanced_signature(const Author& author) {
    if (author.name.empty() || author.email.empty()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Author name and email are required"
        );
    }

    git_signature* signature = nullptr;
    check_advanced(
        git_signature_now(
            &signature,
            author.name.c_str(),
            author.email.c_str()
        ),
        "Create signature"
    );
    return signature;
}

git_commit* advanced_resolve_commit(
    git_repository* repository,
    const std::string& revspec
) {
    git_object* object = nullptr;
    const std::string spec = revspec.empty() ? "HEAD" : revspec;
    check_advanced(
        git_revparse_single(
            &object,
            repository,
            spec.c_str()
        ),
        "Resolve commit"
    );

    git_object* peeled = nullptr;
    const int peel_rc = git_object_peel(
        &peeled,
        object,
        GIT_OBJECT_COMMIT
    );
    git_object_free(object);
    if (peel_rc < 0) {
        throw_advanced_error(peel_rc, "Peel commit");
    }
    return reinterpret_cast<git_commit*>(peeled);
}

void require_idle(git_repository* repository) {
    if (git_repository_state(repository) !=
        GIT_REPOSITORY_STATE_NONE) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Finish or abort the current Git operation first"
        );
    }
}

bool repository_dirty(git_repository* repository) {
    git_status_options options = GIT_STATUS_OPTIONS_INIT;
    options.show = GIT_STATUS_SHOW_INDEX_AND_WORKDIR;
    options.flags =
        GIT_STATUS_OPT_INCLUDE_UNTRACKED |
        GIT_STATUS_OPT_RECURSE_UNTRACKED_DIRS |
        GIT_STATUS_OPT_INCLUDE_UNMODIFIED;

    git_status_list* list = nullptr;
    check_advanced(
        git_status_list_new(&list, repository, &options),
        "Inspect repository status"
    );

    bool dirty = false;
    const size_t count = git_status_list_entrycount(list);
    for (size_t index = 0; index < count; ++index) {
        const git_status_entry* entry =
            git_status_byindex(list, index);
        if (entry != nullptr &&
            entry->status != GIT_STATUS_CURRENT) {
            dirty = true;
            break;
        }
    }

    git_status_list_free(list);
    return dirty;
}

std::string conflict_entry_oid(
    const git_index_entry* entry
) {
    return entry != nullptr
        ? oid_string(&entry->id)
        : "";
}

std::string advanced_conflicts_json(
    git_repository* repository
) {
    git_index* index = nullptr;
    check_advanced(
        git_repository_index(&index, repository),
        "Open conflict index"
    );

    if (!git_index_has_conflicts(index)) {
        git_index_free(index);
        return "[]";
    }

    git_index_conflict_iterator* iterator = nullptr;
    check_advanced(
        git_index_conflict_iterator_new(&iterator, index),
        "Open conflict iterator"
    );

    std::ostringstream out;
    out << "[";
    bool first = true;

    while (true) {
        const git_index_entry* ancestor = nullptr;
        const git_index_entry* ours = nullptr;
        const git_index_entry* theirs = nullptr;
        const int rc = git_index_conflict_next(
            &ancestor,
            &ours,
            &theirs,
            iterator
        );

        if (rc == GIT_ITEROVER) break;
        if (rc < 0) {
            git_index_conflict_iterator_free(iterator);
            git_index_free(index);
            throw_advanced_error(rc, "Read conflict");
        }

        const char* path =
            ours != nullptr && ours->path != nullptr
                ? ours->path
                : (
                    theirs != nullptr &&
                    theirs->path != nullptr
                        ? theirs->path
                        : (
                            ancestor != nullptr &&
                            ancestor->path != nullptr
                                ? ancestor->path
                                : ""
                        )
                );

        if (!first) out << ",";
        first = false;
        out << "{";
        out << "\"path\":" << json_quote(path);
        out << ",\"ancestor\":"
            << json_quote(conflict_entry_oid(ancestor));
        out << ",\"ours\":"
            << json_quote(conflict_entry_oid(ours));
        out << ",\"theirs\":"
            << json_quote(conflict_entry_oid(theirs));
        out << "}";
    }

    out << "]";
    git_index_conflict_iterator_free(iterator);
    git_index_free(index);
    return out.str();
}

std::string merge_result(
    const std::string& state,
    const std::string& oid,
    const std::string& conflicts
) {
    std::ostringstream out;
    out << "{";
    out << "\"state\":" << json_quote(state);
    out << ",\"commitOid\":" << json_quote(oid);
    out << ",\"conflicts\":" << conflicts;
    out << "}";
    return out.str();
}

std::string apply_result(
    const std::string& state,
    const std::string& oid,
    const std::string& conflicts
) {
    std::ostringstream out;
    out << "{";
    out << "\"state\":" << json_quote(state);
    out << ",\"commitOid\":" << json_quote(oid);
    out << ",\"conflicts\":" << conflicts;
    out << "}";
    return out.str();
}

std::string advance_explicit_rebase(
    git_repository* repository,
    git_rebase* rebase,
    const Author& author
) {
    git_signature* signature = advanced_signature(author);
    git_oid last_oid{};
    bool committed = false;

    while (true) {
        git_rebase_operation* operation = nullptr;
        const int next_rc = git_rebase_next(
            &operation,
            rebase
        );

        if (next_rc == GIT_ITEROVER) break;
        if (next_rc < 0) {
            git_signature_free(signature);
            git_rebase_free(rebase);
            throw_advanced_error(
                next_rc,
                "Advance rebase"
            );
        }

        git_index* index = nullptr;
        const int index_rc =
            git_repository_index(&index, repository);
        if (index_rc < 0) {
            git_signature_free(signature);
            git_rebase_free(rebase);
            throw_advanced_error(
                index_rc,
                "Open rebase index"
            );
        }

        const bool conflicts =
            git_index_has_conflicts(index) != 0;
        git_index_free(index);

        if (conflicts) {
            const std::string payload =
                advanced_conflicts_json(repository);
            git_signature_free(signature);
            git_rebase_free(rebase);
            return merge_result(
                "conflicts",
                "",
                payload
            );
        }

        git_oid oid{};
        const int commit_rc = git_rebase_commit(
            &oid,
            rebase,
            nullptr,
            signature,
            nullptr,
            nullptr
        );

        if (commit_rc == GIT_EAPPLIED) {
            continue;
        }
        if (commit_rc < 0) {
            git_signature_free(signature);
            git_rebase_free(rebase);
            throw_advanced_error(
                commit_rc,
                "Commit rebased operation"
            );
        }

        last_oid = oid;
        committed = true;
    }

    const int finish_rc =
        git_rebase_finish(rebase, signature);
    git_signature_free(signature);
    git_rebase_free(rebase);
    check_advanced(finish_rc, "Finish rebase");

    return merge_result(
        "rebased",
        committed ? oid_string(&last_oid) : "",
        "[]"
    );
}

git_commit* state_commit(
    git_repository* repository,
    const char* filename
) {
    const char* git_dir =
        git_repository_path(repository);
    if (git_dir == nullptr) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Repository metadata directory is unavailable"
        );
    }

    std::ifstream stream(
        std::string(git_dir) + filename
    );
    std::string oid_text;
    std::getline(stream, oid_text);

    if (oid_text.empty()) {
        throw GitError(
            GIT_ENOTFOUND,
            0,
            std::string(filename) + " is missing"
        );
    }

    git_oid oid{};
    check_advanced(
        git_oid_fromstr(&oid, oid_text.c_str()),
        "Parse operation commit"
    );

    git_commit* commit = nullptr;
    check_advanced(
        git_commit_lookup(&commit, repository, &oid),
        "Open operation commit"
    );
    return commit;
}

std::string create_state_commit(
    git_repository* repository,
    git_commit* source,
    const Author& author,
    bool revert
) {
    git_index* index = nullptr;
    check_advanced(
        git_repository_index(&index, repository),
        "Open operation index"
    );

    if (git_index_has_conflicts(index)) {
        git_index_free(index);
        return apply_result(
            "conflicts",
            "",
            advanced_conflicts_json(repository)
        );
    }

    git_oid tree_oid{};
    check_advanced(
        git_index_write_tree(&tree_oid, index),
        "Write operation tree"
    );
    check_advanced(
        git_index_write(index),
        "Write operation index"
    );
    git_index_free(index);

    git_tree* tree = nullptr;
    check_advanced(
        git_tree_lookup(&tree, repository, &tree_oid),
        "Open operation tree"
    );

    git_commit* parent =
        advanced_resolve_commit(repository, "HEAD");
    git_signature* committer =
        advanced_signature(author);

    const git_signature* commit_author =
        revert ? committer : git_commit_author(source);

    std::string message;
    if (revert) {
        const char* summary = git_commit_summary(source);
        message =
            "Revert \"" +
            std::string(summary != nullptr ? summary : "commit") +
            "\"\n\nThis reverts commit " +
            oid_string(git_commit_id(source)) +
            ".";
    } else {
        const char* source_message =
            git_commit_message(source);
        message =
            source_message != nullptr
                ? source_message
                : "Cherry-pick commit";
    }

    const git_commit* parents[] = {parent};
    git_oid commit_oid{};
    const int commit_rc = git_commit_create(
        &commit_oid,
        repository,
        "HEAD",
        commit_author,
        committer,
        nullptr,
        message.c_str(),
        tree,
        1,
        parents
    );

    git_signature_free(committer);
    git_commit_free(parent);
    git_tree_free(tree);

    if (commit_rc < 0) {
        throw_advanced_error(
            commit_rc,
            revert
                ? "Create revert commit"
                : "Create cherry-pick commit"
        );
    }

    check_advanced(
        git_repository_state_cleanup(repository),
        "Clean operation state"
    );

    return apply_result(
        "applied",
        oid_string(&commit_oid),
        "[]"
    );
}

void abort_operation(
    git_repository* repository
) {
    git_commit* head =
        advanced_resolve_commit(repository, "HEAD");
    git_checkout_options checkout =
        GIT_CHECKOUT_OPTIONS_INIT;
    checkout.checkout_strategy =
        GIT_CHECKOUT_FORCE |
        GIT_CHECKOUT_RECREATE_MISSING;

    const int reset_rc = git_reset(
        repository,
        reinterpret_cast<git_object*>(head),
        GIT_RESET_HARD,
        &checkout
    );
    git_commit_free(head);
    check_advanced(reset_rc, "Restore HEAD");
    check_advanced(
        git_repository_state_cleanup(repository),
        "Clean repository state"
    );
}

struct StashRow {
    size_t index;
    std::string oid;
    std::string message;
};

int stash_collect(
    size_t index,
    const char* message,
    const git_oid* stash_id,
    void* payload
) {
    auto* rows =
        static_cast<std::vector<StashRow>*>(payload);
    rows->push_back({
        index,
        oid_string(stash_id),
        message != nullptr ? message : "",
    });
    return 0;
}

struct SubmoduleRow {
    std::string name;
    std::string path;
    std::string url;
    std::string head_oid;
    std::string workdir_oid;
    unsigned int status;
};

int submodule_collect(
    git_submodule* submodule,
    const char* name,
    void* payload
) {
    auto* rows =
        static_cast<std::vector<SubmoduleRow>*>(payload);

    unsigned int status = 0;
    git_repository* owner =
        git_submodule_owner(submodule);
    const char* canonical_name =
        git_submodule_name(submodule);
    const std::string resolved_name =
        canonical_name != nullptr
            ? canonical_name
            : (name != nullptr ? name : "");

    if (owner != nullptr && !resolved_name.empty()) {
        git_submodule_status(
            &status,
            owner,
            resolved_name.c_str(),
            GIT_SUBMODULE_IGNORE_UNSPECIFIED
        );
    }

    rows->push_back({
        resolved_name,
        git_submodule_path(submodule) != nullptr
            ? git_submodule_path(submodule)
            : "",
        git_submodule_url(submodule) != nullptr
            ? git_submodule_url(submodule)
            : "",
        oid_string(git_submodule_head_id(submodule)),
        oid_string(git_submodule_wd_id(submodule)),
        status,
    });
    return 0;
}

int credential_callback(
    git_credential** out,
    const char*,
    const char* username_from_url,
    unsigned int allowed_types,
    void* payload
) {
    const auto* credentials =
        static_cast<const Credentials*>(payload);

    if (credentials == nullptr ||
        credentials->password.empty()) {
        return GIT_PASSTHROUGH;
    }

    if ((allowed_types &
         GIT_CREDENTIAL_USERPASS_PLAINTEXT) != 0) {
        const std::string username =
            !credentials->username.empty()
                ? credentials->username
                : (
                    username_from_url != nullptr
                        ? username_from_url
                        : "x-access-token"
                );
        return git_credential_userpass_plaintext_new(
            out,
            username.c_str(),
            credentials->password.c_str()
        );
    }

    if ((allowed_types & GIT_CREDENTIAL_USERNAME) != 0) {
        const std::string username =
            !credentials->username.empty()
                ? credentials->username
                : "x-access-token";
        return git_credential_username_new(
            out,
            username.c_str()
        );
    }

    return GIT_PASSTHROUGH;
}

git_remote_callbacks credential_callbacks(
    const Credentials* credentials
) {
    git_remote_callbacks callbacks =
        GIT_REMOTE_CALLBACKS_INIT;
    callbacks.credentials = credential_callback;
    callbacks.payload =
        const_cast<Credentials*>(credentials);
    return callbacks;
}

}  // namespace

std::string rebase_onto(
    const std::string& repository_path,
    const std::string& upstream_ref,
    const Author& author
) {
    if (upstream_ref.empty()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Rebase target is required"
        );
    }

    git_repository* repository =
        open_advanced_repository(repository_path);
    require_idle(repository);

    if (repository_dirty(repository)) {
        git_repository_free(repository);
        throw GitError(
            GIT_EINVALID,
            0,
            "Rebase requires a clean working tree. Stash or commit changes first"
        );
    }

    git_annotated_commit* upstream = nullptr;
    const int resolve_rc =
        git_annotated_commit_from_revspec(
            &upstream,
            repository,
            upstream_ref.c_str()
        );
    if (resolve_rc < 0) {
        git_repository_free(repository);
        throw_advanced_error(
            resolve_rc,
            "Resolve rebase target"
        );
    }

    git_rebase_options options =
        GIT_REBASE_OPTIONS_INIT;
    options.checkout_options.checkout_strategy =
        GIT_CHECKOUT_SAFE |
        GIT_CHECKOUT_RECREATE_MISSING |
        GIT_CHECKOUT_ALLOW_CONFLICTS |
        GIT_CHECKOUT_CONFLICT_STYLE_MERGE;

    git_rebase* rebase = nullptr;
    const int start_rc = git_rebase_init(
        &rebase,
        repository,
        nullptr,
        upstream,
        nullptr,
        &options
    );
    git_annotated_commit_free(upstream);

    if (start_rc < 0) {
        git_repository_free(repository);
        throw_advanced_error(
            start_rc,
            "Start rebase"
        );
    }

    std::string result;
    try {
        result =
            advance_explicit_rebase(
                repository,
                rebase,
                author
            );
    } catch (...) {
        git_repository_free(repository);
        throw;
    }

    git_repository_free(repository);
    return result;
}

std::string cherry_pick(
    const std::string& repository_path,
    const std::string& commit_ref,
    const Author& author
) {
    if (commit_ref.empty()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Cherry-pick commit is required"
        );
    }

    git_repository* repository =
        open_advanced_repository(repository_path);
    require_idle(repository);

    if (repository_dirty(repository)) {
        git_repository_free(repository);
        throw GitError(
            GIT_EINVALID,
            0,
            "Cherry-pick requires a clean working tree"
        );
    }

    git_commit* source = nullptr;
    try {
        source =
            advanced_resolve_commit(
                repository,
                commit_ref
            );
    } catch (...) {
        git_repository_free(repository);
        throw;
    }

    git_cherrypick_options options =
        GIT_CHERRYPICK_OPTIONS_INIT;
    options.checkout_opts.checkout_strategy =
        GIT_CHECKOUT_SAFE |
        GIT_CHECKOUT_RECREATE_MISSING |
        GIT_CHECKOUT_ALLOW_CONFLICTS |
        GIT_CHECKOUT_CONFLICT_STYLE_MERGE;

    const int rc =
        git_cherrypick(repository, source, &options);
    if (rc < 0) {
        git_commit_free(source);
        git_repository_free(repository);
        throw_advanced_error(rc, "Cherry-pick commit");
    }

    const std::string conflicts =
        advanced_conflicts_json(repository);
    if (conflicts != "[]") {
        git_commit_free(source);
        git_repository_free(repository);
        return apply_result(
            "conflicts",
            "",
            conflicts
        );
    }

    std::string result;
    try {
        result =
            create_state_commit(
                repository,
                source,
                author,
                false
            );
    } catch (...) {
        git_commit_free(source);
        git_repository_free(repository);
        throw;
    }

    git_commit_free(source);
    git_repository_free(repository);
    return result;
}

std::string continue_cherry_pick(
    const std::string& repository_path,
    const Author& author
) {
    git_repository* repository =
        open_advanced_repository(repository_path);

    const int state =
        git_repository_state(repository);
    if (state != GIT_REPOSITORY_STATE_CHERRYPICK &&
        state != GIT_REPOSITORY_STATE_CHERRYPICK_SEQUENCE) {
        git_repository_free(repository);
        throw GitError(
            GIT_EINVALID,
            0,
            "No cherry-pick is in progress"
        );
    }

    git_commit* source = nullptr;
    try {
        source =
            state_commit(
                repository,
                "CHERRY_PICK_HEAD"
            );
    } catch (...) {
        git_repository_free(repository);
        throw;
    }

    std::string result;
    try {
        result =
            create_state_commit(
                repository,
                source,
                author,
                false
            );
    } catch (...) {
        git_commit_free(source);
        git_repository_free(repository);
        throw;
    }

    git_commit_free(source);
    git_repository_free(repository);
    return result;
}

void abort_cherry_pick(
    const std::string& repository_path
) {
    git_repository* repository =
        open_advanced_repository(repository_path);
    const int state =
        git_repository_state(repository);
    if (state != GIT_REPOSITORY_STATE_CHERRYPICK &&
        state != GIT_REPOSITORY_STATE_CHERRYPICK_SEQUENCE) {
        git_repository_free(repository);
        throw GitError(
            GIT_EINVALID,
            0,
            "No cherry-pick is in progress"
        );
    }

    try {
        abort_operation(repository);
    } catch (...) {
        git_repository_free(repository);
        throw;
    }
    git_repository_free(repository);
}

std::string stashes(
    const std::string& repository_path
) {
    git_repository* repository =
        open_advanced_repository(repository_path);
    std::vector<StashRow> rows;
    check_advanced(
        git_stash_foreach(
            repository,
            stash_collect,
            &rows
        ),
        "List stashes"
    );

    std::ostringstream out;
    out << "[";
    for (size_t i = 0; i < rows.size(); ++i) {
        if (i > 0) out << ",";
        out << "{";
        out << "\"index\":" << rows[i].index;
        out << ",\"oid\":" << json_quote(rows[i].oid);
        out << ",\"message\":"
            << json_quote(rows[i].message);
        out << "}";
    }
    out << "]";

    git_repository_free(repository);
    return out.str();
}

std::string save_stash(
    const std::string& repository_path,
    const std::string& message,
    const Author& author,
    bool include_untracked
) {
    git_repository* repository =
        open_advanced_repository(repository_path);
    require_idle(repository);

    git_signature* signature =
        advanced_signature(author);
    git_oid oid{};
    const uint32_t flags =
        include_untracked
            ? GIT_STASH_INCLUDE_UNTRACKED
            : GIT_STASH_DEFAULT;

    const int rc = git_stash_save(
        &oid,
        repository,
        signature,
        message.empty()
            ? "Nexora Git stash"
            : message.c_str(),
        flags
    );

    git_signature_free(signature);
    git_repository_free(repository);
    check_advanced(rc, "Save stash");
    return oid_string(&oid);
}

void apply_stash(
    const std::string& repository_path,
    size_t index,
    bool pop
) {
    git_repository* repository =
        open_advanced_repository(repository_path);
    require_idle(repository);

    git_stash_apply_options options =
        GIT_STASH_APPLY_OPTIONS_INIT;
    options.checkout_options.checkout_strategy =
        GIT_CHECKOUT_SAFE |
        GIT_CHECKOUT_RECREATE_MISSING;

    const int rc = pop
        ? git_stash_pop(
            repository,
            index,
            &options
        )
        : git_stash_apply(
            repository,
            index,
            &options
        );

    git_repository_free(repository);
    check_advanced(
        rc,
        pop ? "Pop stash" : "Apply stash"
    );
}

void drop_stash(
    const std::string& repository_path,
    size_t index
) {
    git_repository* repository =
        open_advanced_repository(repository_path);
    const int rc =
        git_stash_drop(repository, index);
    git_repository_free(repository);
    check_advanced(rc, "Drop stash");
}

void reset_to(
    const std::string& repository_path,
    const std::string& target_ref,
    const std::string& mode
) {
    if (target_ref.empty()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Reset target is required"
        );
    }

    git_reset_t reset_mode = GIT_RESET_MIXED;
    if (mode == "soft") {
        reset_mode = GIT_RESET_SOFT;
    } else if (mode == "mixed") {
        reset_mode = GIT_RESET_MIXED;
    } else if (mode == "hard") {
        reset_mode = GIT_RESET_HARD;
    } else {
        throw GitError(
            GIT_EINVALID,
            0,
            "Reset mode must be soft, mixed, or hard"
        );
    }

    git_repository* repository =
        open_advanced_repository(repository_path);
    require_idle(repository);

    git_commit* target = nullptr;
    try {
        target =
            advanced_resolve_commit(
                repository,
                target_ref
            );
    } catch (...) {
        git_repository_free(repository);
        throw;
    }

    git_checkout_options checkout =
        GIT_CHECKOUT_OPTIONS_INIT;
    checkout.checkout_strategy =
        reset_mode == GIT_RESET_HARD
            ? (
                GIT_CHECKOUT_FORCE |
                GIT_CHECKOUT_RECREATE_MISSING
            )
            : GIT_CHECKOUT_SAFE;

    const int rc = git_reset(
        repository,
        reinterpret_cast<git_object*>(target),
        reset_mode,
        &checkout
    );

    git_commit_free(target);
    git_repository_free(repository);
    check_advanced(rc, "Reset repository");
}

std::string revert_commit(
    const std::string& repository_path,
    const std::string& commit_ref,
    const Author& author
) {
    if (commit_ref.empty()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Revert commit is required"
        );
    }

    git_repository* repository =
        open_advanced_repository(repository_path);
    require_idle(repository);

    if (repository_dirty(repository)) {
        git_repository_free(repository);
        throw GitError(
            GIT_EINVALID,
            0,
            "Revert requires a clean working tree"
        );
    }

    git_commit* source = nullptr;
    try {
        source =
            advanced_resolve_commit(
                repository,
                commit_ref
            );
    } catch (...) {
        git_repository_free(repository);
        throw;
    }

    git_revert_options options =
        GIT_REVERT_OPTIONS_INIT;
    options.checkout_opts.checkout_strategy =
        GIT_CHECKOUT_SAFE |
        GIT_CHECKOUT_RECREATE_MISSING |
        GIT_CHECKOUT_ALLOW_CONFLICTS |
        GIT_CHECKOUT_CONFLICT_STYLE_MERGE;

    const int rc =
        git_revert(repository, source, &options);
    if (rc < 0) {
        git_commit_free(source);
        git_repository_free(repository);
        throw_advanced_error(rc, "Revert commit");
    }

    const std::string conflicts =
        advanced_conflicts_json(repository);
    if (conflicts != "[]") {
        git_commit_free(source);
        git_repository_free(repository);
        return apply_result(
            "conflicts",
            "",
            conflicts
        );
    }

    std::string result;
    try {
        result =
            create_state_commit(
                repository,
                source,
                author,
                true
            );
    } catch (...) {
        git_commit_free(source);
        git_repository_free(repository);
        throw;
    }

    git_commit_free(source);
    git_repository_free(repository);
    return result;
}

std::string continue_revert(
    const std::string& repository_path,
    const Author& author
) {
    git_repository* repository =
        open_advanced_repository(repository_path);

    const int state =
        git_repository_state(repository);
    if (state != GIT_REPOSITORY_STATE_REVERT &&
        state != GIT_REPOSITORY_STATE_REVERT_SEQUENCE) {
        git_repository_free(repository);
        throw GitError(
            GIT_EINVALID,
            0,
            "No revert is in progress"
        );
    }

    git_commit* source = nullptr;
    try {
        source =
            state_commit(repository, "REVERT_HEAD");
    } catch (...) {
        git_repository_free(repository);
        throw;
    }

    std::string result;
    try {
        result =
            create_state_commit(
                repository,
                source,
                author,
                true
            );
    } catch (...) {
        git_commit_free(source);
        git_repository_free(repository);
        throw;
    }

    git_commit_free(source);
    git_repository_free(repository);
    return result;
}

void abort_revert(
    const std::string& repository_path
) {
    git_repository* repository =
        open_advanced_repository(repository_path);
    const int state =
        git_repository_state(repository);
    if (state != GIT_REPOSITORY_STATE_REVERT &&
        state != GIT_REPOSITORY_STATE_REVERT_SEQUENCE) {
        git_repository_free(repository);
        throw GitError(
            GIT_EINVALID,
            0,
            "No revert is in progress"
        );
    }

    try {
        abort_operation(repository);
    } catch (...) {
        git_repository_free(repository);
        throw;
    }
    git_repository_free(repository);
}

std::string tags(
    const std::string& repository_path
) {
    git_repository* repository =
        open_advanced_repository(repository_path);
    git_reference_iterator* iterator = nullptr;
    check_advanced(
        git_reference_iterator_glob_new(
            &iterator,
            repository,
            "refs/tags/*"
        ),
        "Open tag iterator"
    );

    std::ostringstream out;
    out << "[";
    bool first = true;

    while (true) {
        git_reference* reference = nullptr;
        const int next_rc =
            git_reference_next(&reference, iterator);
        if (next_rc == GIT_ITEROVER) break;
        if (next_rc < 0) {
            git_reference_iterator_free(iterator);
            git_repository_free(repository);
            throw_advanced_error(next_rc, "Read tag");
        }

        const char* shorthand =
            git_reference_shorthand(reference);
        const git_oid* reference_oid =
            git_reference_target(reference);

        git_object* object = nullptr;
        bool annotated = false;
        std::string target_oid =
            oid_string(reference_oid);
        std::string message;
        std::string tagger_name;
        std::string tagger_email;

        if (reference_oid != nullptr &&
            git_object_lookup(
                &object,
                repository,
                reference_oid,
                GIT_OBJECT_ANY
            ) == 0) {
            if (git_object_type(object) ==
                GIT_OBJECT_TAG) {
                annotated = true;
                auto* tag =
                    reinterpret_cast<git_tag*>(object);
                target_oid =
                    oid_string(git_tag_target_id(tag));
                const char* tag_message =
                    git_tag_message(tag);
                message =
                    tag_message != nullptr
                        ? tag_message
                        : "";
                const git_signature* tagger =
                    git_tag_tagger(tag);
                if (tagger != nullptr) {
                    tagger_name =
                        tagger->name != nullptr
                            ? tagger->name
                            : "";
                    tagger_email =
                        tagger->email != nullptr
                            ? tagger->email
                            : "";
                }
            }
            git_object_free(object);
        }

        if (!first) out << ",";
        first = false;
        out << "{";
        out << "\"name\":"
            << json_quote(
                shorthand != nullptr
                    ? shorthand
                    : ""
            );
        out << ",\"targetOid\":"
            << json_quote(target_oid);
        out << ",\"annotated\":"
            << (annotated ? "true" : "false");
        out << ",\"message\":"
            << json_quote(message);
        out << ",\"taggerName\":"
            << json_quote(tagger_name);
        out << ",\"taggerEmail\":"
            << json_quote(tagger_email);
        out << "}";

        git_reference_free(reference);
    }

    out << "]";
    git_reference_iterator_free(iterator);
    git_repository_free(repository);
    return out.str();
}

std::string create_tag(
    const std::string& repository_path,
    const std::string& name,
    const std::string& target_ref,
    const std::string& message,
    const Author& author,
    bool annotated
) {
    if (name.empty()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Tag name is required"
        );
    }

    git_repository* repository =
        open_advanced_repository(repository_path);
    require_idle(repository);

    git_object* target = nullptr;
    const std::string target_spec =
        target_ref.empty() ? "HEAD" : target_ref;
    const int resolve_rc =
        git_revparse_single(
            &target,
            repository,
            target_spec.c_str()
        );
    if (resolve_rc < 0) {
        git_repository_free(repository);
        throw_advanced_error(
            resolve_rc,
            "Resolve tag target"
        );
    }

    git_oid oid{};
    int rc = 0;

    if (annotated) {
        git_signature* signature =
            advanced_signature(author);
        rc = git_tag_create(
            &oid,
            repository,
            name.c_str(),
            target,
            signature,
            message.empty()
                ? name.c_str()
                : message.c_str(),
            0
        );
        git_signature_free(signature);
    } else {
        rc = git_tag_create_lightweight(
            &oid,
            repository,
            name.c_str(),
            target,
            0
        );
    }

    git_object_free(target);
    git_repository_free(repository);
    check_advanced(rc, "Create tag");
    return oid_string(&oid);
}

void delete_tag(
    const std::string& repository_path,
    const std::string& name
) {
    if (name.empty()) {
        throw GitError(
            GIT_EINVALID,
            0,
            "Tag name is required"
        );
    }

    git_repository* repository =
        open_advanced_repository(repository_path);
    const int rc =
        git_tag_delete(repository, name.c_str());
    git_repository_free(repository);
    check_advanced(rc, "Delete tag");
}

std::string submodules(
    const std::string& repository_path
) {
    git_repository* repository =
        open_advanced_repository(repository_path);
    std::vector<SubmoduleRow> rows;
    check_advanced(
        git_submodule_foreach(
            repository,
            submodule_collect,
            &rows
        ),
        "List submodules"
    );

    std::ostringstream out;
    out << "[";
    for (size_t i = 0; i < rows.size(); ++i) {
        if (i > 0) out << ",";
        const bool initialized =
            (rows[i].status &
             GIT_SUBMODULE_STATUS_IN_CONFIG) != 0 &&
            (
                !rows[i].head_oid.empty() ||
                !rows[i].workdir_oid.empty()
            );
        out << "{";
        out << "\"name\":"
            << json_quote(rows[i].name);
        out << ",\"path\":"
            << json_quote(rows[i].path);
        out << ",\"url\":"
            << json_quote(rows[i].url);
        out << ",\"headOid\":"
            << json_quote(rows[i].head_oid);
        out << ",\"workdirOid\":"
            << json_quote(rows[i].workdir_oid);
        out << ",\"status\":"
            << rows[i].status;
        out << ",\"initialized\":"
            << (initialized ? "true" : "false");
        out << "}";
    }
    out << "]";

    git_repository_free(repository);
    return out.str();
}

void sync_submodule(
    const std::string& repository_path,
    const std::string& name
) {
    git_repository* repository =
        open_advanced_repository(repository_path);
    git_submodule* submodule = nullptr;
    const int lookup_rc =
        git_submodule_lookup(
            &submodule,
            repository,
            name.c_str()
        );
    if (lookup_rc < 0) {
        git_repository_free(repository);
        throw_advanced_error(
            lookup_rc,
            "Open submodule"
        );
    }

    const int rc =
        git_submodule_sync(submodule);
    git_submodule_free(submodule);
    git_repository_free(repository);
    check_advanced(rc, "Sync submodule");
}

void update_submodule(
    const std::string& repository_path,
    const std::string& name,
    bool initialize,
    const Credentials& credentials
) {
    git_repository* repository =
        open_advanced_repository(repository_path);
    require_idle(repository);

    git_submodule* submodule = nullptr;
    const int lookup_rc =
        git_submodule_lookup(
            &submodule,
            repository,
            name.c_str()
        );
    if (lookup_rc < 0) {
        git_repository_free(repository);
        throw_advanced_error(
            lookup_rc,
            "Open submodule"
        );
    }

    git_submodule_update_options options =
        GIT_SUBMODULE_UPDATE_OPTIONS_INIT;
    options.checkout_opts.checkout_strategy =
        GIT_CHECKOUT_SAFE |
        GIT_CHECKOUT_RECREATE_MISSING;
    options.fetch_opts.callbacks =
        credential_callbacks(&credentials);

    const int rc = git_submodule_update(
        submodule,
        initialize ? 1 : 0,
        &options
    );

    git_submodule_free(submodule);
    git_repository_free(repository);
    check_advanced(rc, "Update submodule");
}

}  // namespace nexora::git
