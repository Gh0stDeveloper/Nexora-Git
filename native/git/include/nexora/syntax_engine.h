#pragma once

#include <string>

namespace nexora::syntax {

std::string version();
bool supports_language(const std::string& grammar);
std::string analyze(
    const std::string& grammar,
    const std::string& source
);

}  // namespace nexora::syntax
