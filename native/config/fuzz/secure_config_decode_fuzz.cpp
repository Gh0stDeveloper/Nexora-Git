#include <cstddef>
#include <cstdint>

#include "nexora_secure_config.generated.hpp"

extern "C" int LLVMFuzzerTestOneInput(
    const std::uint8_t* data,
    std::size_t size
) {
    if (data == nullptr || size == 0) {
        return 0;
    }

    const std::size_t offset = data[0];
    const nexora::secure_config::EncodedValue value{
        data + 1,
        size - 1,
        offset,
    };

    const std::string decoded = nexora::secure_config::Decode(value);
    if (decoded.size() != size - 1) {
        __builtin_trap();
    }

    return 0;
}
