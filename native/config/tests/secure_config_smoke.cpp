#include <cassert>
#include <string>

#include "nexora_secure_config.generated.hpp"

int main() {
    assert(
        nexora::secure_config::GithubClientId() ==
        "Iv1.NexoraNativeConfigTest"
    );
    assert(
        nexora::secure_config::BrokerBaseUrl() ==
        "https://native-config.example"
    );
    assert(
        nexora::secure_config::GithubCallbackUrl() ==
        "https://native-config.example/oauth/callback"
    );
    assert(
        nexora::secure_config::AppCallbackUri() ==
        "https://native-config.example/oauth/android/callback"
    );
    return 0;
}
