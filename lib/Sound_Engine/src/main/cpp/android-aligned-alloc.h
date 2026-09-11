#pragma once

#include <cstddef>

#if defined(__ANDROID__)

extern "C" void* aligned_alloc(std::size_t alignment, std::size_t size) noexcept;

#include <cstdlib>

extern "C" inline void* aligned_alloc(std::size_t alignment, std::size_t size) noexcept {
    void* pointer = nullptr;
    if (::posix_memalign(&pointer, alignment, size) != 0) {
        return nullptr;
    }
    return pointer;
}

#endif