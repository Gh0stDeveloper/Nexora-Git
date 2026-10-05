#pragma once

#include <exception>
#include <string>
#include <vector>

namespace nexora::git {

struct Credentials {
    std::string username;
    std::string password;

    bool empty() const {
        return password.empty();
    }
};

struct Author {
    std::string name;
    std::string email;
};

class GitError final : public std::exception {
public:
    GitError(int code, int klass, std::string message);

    const char* what() const noexcept override;
    int code() const noexcept;
    int klass() const noexcept;

private:
    int code_;
    int klass_;
    std::string message_;
};

void initialize(
    const std::string& home_directory,
    const std::string& certificate_directory
);

std::string version();
std::string init_repository(const std::string& path);
std::string clone_repository(
    const std::string& url,
    const std::string& destination,
    const Credentials& credentials
);
std::string status(const std::string& repository_path);
std::string remote_url(
    const std::string& repository_path,
    const std::string& remote
);
std::string remotes(const std::string& repository_path);
void add_remote(
    const std::string& repository_path,
    const std::string& name,
    const std::string& url
);
void rename_remote(
    const std::string& repository_path,
    const std::string& old_name,
    const std::string& new_name
);
void remove_remote(
    const std::string& repository_path,
    const std::string& name
);
void stage(
    const std::string& repository_path,
    const std::vector<std::string>& paths
);
void unstage(
    const std::string& repository_path,
    const std::vector<std::string>& paths
);
std::string commit(
    const std::string& repository_path,
    const std::string& message,
    const Author& author
);
std::string branches(const std::string& repository_path);
void set_upstream(
    const std::string& repository_path,
    const std::string& branch,
    const std::string& upstream
);
std::string divergence(
    const std::string& repository_path,
    const std::string& local_ref,
    const std::string& upstream_ref
);
std::string repository_state(
    const std::string& repository_path
);
void create_branch(
    const std::string& repository_path,
    const std::string& name,
    const std::string& start_point
);
void checkout(
    const std::string& repository_path,
    const std::string& ref
);
void fetch(
    const std::string& repository_path,
    const std::string& remote,
    const Credentials& credentials
);
std::string pull(
    const std::string& repository_path,
    const std::string& remote,
    const Author& author,
    const Credentials& credentials
);
std::string pull_with_strategy(
    const std::string& repository_path,
    const std::string& remote,
    const std::string& strategy,
    const Author& author,
    const Credentials& credentials
);
std::string continue_merge(
    const std::string& repository_path,
    const Author& author
);
std::string continue_rebase(
    const std::string& repository_path,
    const Author& author
);
void abort_rebase(
    const std::string& repository_path
);
std::string push(
    const std::string& repository_path,
    const std::string& remote,
    const std::string& refspec,
    const Credentials& credentials
);
std::string push_force_with_lease(
    const std::string& repository_path,
    const std::string& remote,
    const std::string& refspec,
    const std::string& expected_remote_oid,
    const Credentials& credentials
);
std::string diff(
    const std::string& repository_path,
    const std::string& mode,
    const std::string& relative_path = ""
);
std::string merge(
    const std::string& repository_path,
    const std::string& ref,
    const Author& author
);
std::string conflicts(const std::string& repository_path);
void resolve_conflict(
    const std::string& repository_path,
    const std::string& path,
    const std::string& resolution
);
std::string history(
    const std::string& repository_path,
    const std::string& relative_path,
    int limit
);
std::string blame(
    const std::string& repository_path,
    const std::string& relative_path
);

}  // namespace nexora::git
