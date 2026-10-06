#include "nexora/syntax_engine.h"

#include <tree_sitter/api.h>

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <sstream>
#include <stdexcept>
#include <string>
#include <unordered_set>
#include <utility>
#include <vector>

extern "C" {
const TSLanguage* tree_sitter_kotlin();
const TSLanguage* tree_sitter_java();
const TSLanguage* tree_sitter_json();
const TSLanguage* tree_sitter_python();
const TSLanguage* tree_sitter_javascript();
const TSLanguage* tree_sitter_typescript();
const TSLanguage* tree_sitter_tsx();
}

namespace nexora::syntax {
namespace {

constexpr size_t kMaxVisitedNodes = 12000;
constexpr size_t kMaxSpans = 5000;
constexpr size_t kMaxSymbols = 1000;
constexpr size_t kMaxDiagnostics = 200;

struct Utf16Map {
    std::vector<uint32_t> offsets;
    std::vector<uint32_t> line_starts;
};

struct Span {
    uint32_t start;
    uint32_t end;
    std::string kind;
};

struct Symbol {
    std::string name;
    std::string kind;
    uint32_t start;
    uint32_t end;
    uint32_t line;
};

struct Diagnostic {
    std::string severity;
    std::string message;
    uint32_t start;
    uint32_t end;
    uint32_t line;
    uint32_t column;
};

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

std::string quote(const std::string& value) {
    return "\"" + escape_json(value) + "\"";
}

std::string lower(std::string value) {
    std::transform(
        value.begin(),
        value.end(),
        value.begin(),
        [](unsigned char ch) {
            return static_cast<char>(std::tolower(ch));
        }
    );
    return value;
}

const TSLanguage* language_for(const std::string& grammar) {
    const std::string normalized = lower(grammar);
    if (normalized == "kotlin") return tree_sitter_kotlin();
    if (normalized == "java") return tree_sitter_java();
    if (normalized == "json") return tree_sitter_json();
    if (normalized == "python") return tree_sitter_python();
    if (normalized == "javascript") return tree_sitter_javascript();
    if (normalized == "typescript") return tree_sitter_typescript();
    if (normalized == "tsx") return tree_sitter_tsx();
    return nullptr;
}

Utf16Map build_utf16_map(const std::string& source) {
    Utf16Map result;
    result.offsets.assign(source.size() + 1, 0);
    result.line_starts.push_back(0);

    size_t byte = 0;
    uint32_t utf16 = 0;
    while (byte < source.size()) {
        result.offsets[byte] = utf16;
        const unsigned char first =
            static_cast<unsigned char>(source[byte]);

        size_t length = 1;
        uint32_t codepoint = first;
        if ((first & 0xE0) == 0xC0 && byte + 1 < source.size()) {
            length = 2;
            codepoint = first & 0x1F;
        } else if (
            (first & 0xF0) == 0xE0 &&
            byte + 2 < source.size()
        ) {
            length = 3;
            codepoint = first & 0x0F;
        } else if (
            (first & 0xF8) == 0xF0 &&
            byte + 3 < source.size()
        ) {
            length = 4;
            codepoint = first & 0x07;
        }

        bool valid = length == 1 || first >= 0xC2;
        for (size_t index = 1; index < length && valid; ++index) {
            const unsigned char next =
                static_cast<unsigned char>(source[byte + index]);
            if ((next & 0xC0) != 0x80) {
                valid = false;
                break;
            }
            codepoint = (codepoint << 6) | (next & 0x3F);
        }
        if (!valid) {
            length = 1;
            codepoint = first;
        }

        for (size_t index = 1; index < length; ++index) {
            result.offsets[byte + index] = utf16;
        }

        if (source[byte] == '\n') {
            result.line_starts.push_back(
                static_cast<uint32_t>(byte + 1)
            );
        }

        byte += length;
        utf16 += codepoint > 0xFFFF ? 2 : 1;
        result.offsets[byte] = utf16;
    }
    return result;
}

uint32_t utf16_offset(
    const Utf16Map& map,
    uint32_t byte
) {
    if (map.offsets.empty()) return 0;
    const size_t safe = std::min<size_t>(
        byte,
        map.offsets.size() - 1
    );
    return map.offsets[safe];
}

uint32_t utf16_column(
    const Utf16Map& map,
    uint32_t byte,
    uint32_t row
) {
    const uint32_t start =
        row < map.line_starts.size()
            ? map.line_starts[row]
            : 0;
    const uint32_t absolute = utf16_offset(map, byte);
    const uint32_t line = utf16_offset(map, start);
    return absolute >= line ? absolute - line : 0;
}

std::string slice(
    const std::string& source,
    uint32_t start,
    uint32_t end
) {
    if (start >= source.size() || end <= start) return "";
    const size_t safe_end = std::min<size_t>(end, source.size());
    return source.substr(start, safe_end - start);
}

bool contains(
    const std::string& value,
    const std::string& needle
) {
    return value.find(needle) != std::string::npos;
}

const std::unordered_set<std::string>& keywords_for(
    const std::string& grammar
) {
    static const std::unordered_set<std::string> kotlin = {
        "as", "break", "class", "continue", "do", "else", "false",
        "for", "fun", "if", "in", "interface", "is", "null",
        "object", "package", "return", "super", "this", "throw",
        "true", "try", "typealias", "typeof", "val", "var", "when",
        "while", "by", "catch", "constructor", "delegate", "dynamic",
        "field", "file", "finally", "get", "import", "init", "param",
        "property", "receiver", "set", "setparam", "where", "actual",
        "abstract", "annotation", "companion", "const", "crossinline",
        "data", "enum", "expect", "external", "final", "infix",
        "inline", "inner", "internal", "lateinit", "noinline", "open",
        "operator", "out", "override", "private", "protected", "public",
        "reified", "sealed", "suspend", "tailrec", "vararg"
    };
    static const std::unordered_set<std::string> java = {
        "abstract", "assert", "boolean", "break", "byte", "case",
        "catch", "char", "class", "const", "continue", "default", "do",
        "double", "else", "enum", "extends", "final", "finally",
        "float", "for", "goto", "if", "implements", "import",
        "instanceof", "int", "interface", "long", "native", "new",
        "package", "private", "protected", "public", "return", "short",
        "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile",
        "while", "true", "false", "null", "record", "sealed", "permits"
    };
    static const std::unordered_set<std::string> javascript = {
        "async", "await", "break", "case", "catch", "class", "const",
        "continue", "debugger", "default", "delete", "do", "else",
        "export", "extends", "false", "finally", "for", "from",
        "function", "get", "if", "import", "in", "instanceof", "let",
        "new", "null", "of", "return", "set", "static", "super",
        "switch", "this", "throw", "true", "try", "typeof", "undefined",
        "var", "void", "while", "with", "yield"
    };
    static const std::unordered_set<std::string> typescript = [] {
        auto result = javascript;
        const char* values[] = {
            "interface", "type", "enum", "namespace", "declare",
            "implements", "private", "protected", "public", "readonly",
            "abstract", "keyof", "infer", "is", "asserts", "satisfies",
            "unknown", "never", "any"
        };
        for (const char* value : values) result.insert(value);
        return result;
    }();
    static const std::unordered_set<std::string> python = {
        "False", "None", "True", "and", "as", "assert", "async",
        "await", "break", "class", "continue", "def", "del", "elif",
        "else", "except", "finally", "for", "from", "global", "if",
        "import", "in", "is", "lambda", "nonlocal", "not", "or",
        "pass", "raise", "return", "try", "while", "with", "yield",
        "match", "case"
    };
    static const std::unordered_set<std::string> empty;

    const std::string normalized = lower(grammar);
    if (normalized == "kotlin") return kotlin;
    if (normalized == "java") return java;
    if (normalized == "javascript") return javascript;
    if (normalized == "typescript" || normalized == "tsx") {
        return typescript;
    }
    if (normalized == "python") return python;
    return empty;
}

std::string span_kind(
    TSNode node,
    const std::string& grammar,
    const std::string& source
) {
    const std::string normalized = lower(ts_node_type(node));

    if (contains(normalized, "comment")) return "comment";
    if (
        contains(normalized, "string") ||
        normalized == "character_literal" ||
        normalized == "char_literal"
    ) {
        return "string";
    }
    if (
        contains(normalized, "integer") ||
        contains(normalized, "float") ||
        normalized == "number" ||
        normalized == "number_literal"
    ) {
        return "number";
    }
    if (
        normalized == "type_identifier" ||
        normalized == "user_type" ||
        normalized == "predefined_type"
    ) {
        return "type";
    }

    if (ts_node_child_count(node) == 0) {
        const std::string token = slice(
            source,
            ts_node_start_byte(node),
            ts_node_end_byte(node)
        );
        if (keywords_for(grammar).count(token) > 0) {
            return "keyword";
        }
    }
    return "";
}

std::string declaration_kind(const std::string& type) {
    const std::string normalized = lower(type);
    if (
        contains(normalized, "class_declaration") ||
        contains(normalized, "interface_declaration") ||
        contains(normalized, "object_declaration") ||
        contains(normalized, "enum_declaration") ||
        contains(normalized, "type_alias") ||
        normalized == "class_definition"
    ) {
        return "type";
    }
    if (
        contains(normalized, "function_declaration") ||
        contains(normalized, "function_definition") ||
        contains(normalized, "method_declaration")
    ) {
        return "function";
    }
    if (
        contains(normalized, "property_declaration") ||
        contains(normalized, "variable_declaration") ||
        contains(normalized, "lexical_declaration") ||
        contains(normalized, "const_declaration")
    ) {
        return "property";
    }
    return "";
}

TSNode find_identifier(TSNode node, int depth = 0) {
    TSNode named = ts_node_child_by_field_name(node, "name", 4);
    if (!ts_node_is_null(named)) return named;
    if (depth >= 3) return TSNode{};

    const uint32_t count = ts_node_named_child_count(node);
    for (uint32_t index = 0; index < count; ++index) {
        TSNode child = ts_node_named_child(node, index);
        const std::string type = lower(ts_node_type(child));
        if (
            type == "identifier" ||
            type == "type_identifier" ||
            type == "simple_identifier"
        ) {
            return child;
        }
        TSNode nested = find_identifier(child, depth + 1);
        if (!ts_node_is_null(nested)) return nested;
    }
    return TSNode{};
}

std::string serialize(
    const std::string& grammar,
    TSNode root,
    bool truncated,
    const std::vector<Span>& spans,
    const std::vector<Symbol>& symbols,
    const std::vector<Diagnostic>& diagnostics
) {
    std::ostringstream out;
    out << "{"
        << "\"engine\":\"tree-sitter\","
        << "\"language\":" << quote(grammar) << ","
        << "\"rootType\":" << quote(ts_node_type(root)) << ","
        << "\"hasErrors\":"
        << (ts_node_has_error(root) ? "true" : "false") << ","
        << "\"truncated\":" << (truncated ? "true" : "false") << ","
        << "\"spans\":[";

    for (size_t index = 0; index < spans.size(); ++index) {
        if (index > 0) out << ',';
        const auto& span = spans[index];
        out << "{"
            << "\"start\":" << span.start << ','
            << "\"end\":" << span.end << ','
            << "\"kind\":" << quote(span.kind)
            << "}";
    }
    out << "],\"symbols\":[";
    for (size_t index = 0; index < symbols.size(); ++index) {
        if (index > 0) out << ',';
        const auto& symbol = symbols[index];
        out << "{"
            << "\"name\":" << quote(symbol.name) << ','
            << "\"kind\":" << quote(symbol.kind) << ','
            << "\"start\":" << symbol.start << ','
            << "\"end\":" << symbol.end << ','
            << "\"line\":" << symbol.line
            << "}";
    }
    out << "],\"diagnostics\":[";
    for (size_t index = 0; index < diagnostics.size(); ++index) {
        if (index > 0) out << ',';
        const auto& diagnostic = diagnostics[index];
        out << "{"
            << "\"severity\":" << quote(diagnostic.severity) << ','
            << "\"message\":" << quote(diagnostic.message) << ','
            << "\"start\":" << diagnostic.start << ','
            << "\"end\":" << diagnostic.end << ','
            << "\"line\":" << diagnostic.line << ','
            << "\"column\":" << diagnostic.column
            << "}";
    }
    out << "]}";
    return out.str();
}

}  // namespace

std::string version() {
    return std::string("Tree-sitter ABI ") +
        std::to_string(TREE_SITTER_LANGUAGE_VERSION);
}

bool supports_language(const std::string& grammar) {
    return language_for(grammar) != nullptr;
}

std::string analyze(
    const std::string& grammar,
    const std::string& source
) {
    const TSLanguage* language = language_for(grammar);
    if (language == nullptr) {
        throw std::invalid_argument(
            "Unsupported Tree-sitter grammar: " + grammar
        );
    }

    TSParser* parser = ts_parser_new();
    if (parser == nullptr) {
        throw std::runtime_error("Unable to create Tree-sitter parser");
    }

    if (!ts_parser_set_language(parser, language)) {
        ts_parser_delete(parser);
        throw std::runtime_error(
            "Tree-sitter grammar ABI is incompatible with the runtime"
        );
    }

    TSTree* tree = ts_parser_parse_string(
        parser,
        nullptr,
        source.c_str(),
        static_cast<uint32_t>(source.size())
    );
    ts_parser_delete(parser);
    if (tree == nullptr) {
        throw std::runtime_error("Tree-sitter parse failed");
    }

    const TSNode root = ts_tree_root_node(tree);
    const Utf16Map mapping = build_utf16_map(source);
    std::vector<Span> spans;
    std::vector<Symbol> symbols;
    std::vector<Diagnostic> diagnostics;
    std::unordered_set<std::string> seen_symbols;
    std::vector<TSNode> stack{root};
    size_t visited = 0;
    bool truncated = false;

    while (!stack.empty()) {
        TSNode node = stack.back();
        stack.pop_back();
        ++visited;
        if (visited > kMaxVisitedNodes) {
            truncated = true;
            break;
        }

        const uint32_t start_byte = ts_node_start_byte(node);
        const uint32_t end_byte = ts_node_end_byte(node);
        const uint32_t start = utf16_offset(mapping, start_byte);
        const uint32_t end = utf16_offset(mapping, end_byte);

        if (spans.size() < kMaxSpans) {
            const std::string kind =
                span_kind(node, grammar, source);
            if (!kind.empty() && end > start) {
                spans.push_back({start, end, kind});
            }
        }

        const std::string kind =
            declaration_kind(ts_node_type(node));
        if (!kind.empty() && symbols.size() < kMaxSymbols) {
            const TSNode name = find_identifier(node);
            if (!ts_node_is_null(name)) {
                const uint32_t name_start_byte =
                    ts_node_start_byte(name);
                const uint32_t name_end_byte =
                    ts_node_end_byte(name);
                const std::string value = slice(
                    source,
                    name_start_byte,
                    name_end_byte
                );
                const uint32_t name_start =
                    utf16_offset(mapping, name_start_byte);
                const uint32_t name_end =
                    utf16_offset(mapping, name_end_byte);
                const TSPoint point = ts_node_start_point(name);
                const std::string key =
                    kind + ":" + value + ":" +
                    std::to_string(name_start);
                if (
                    !value.empty() &&
                    seen_symbols.insert(key).second
                ) {
                    symbols.push_back({
                        value,
                        kind,
                        name_start,
                        name_end,
                        point.row + 1
                    });
                    if (spans.size() < kMaxSpans) {
                        spans.push_back({
                            name_start,
                            name_end,
                            kind
                        });
                    }
                }
            }
        }

        if (
            diagnostics.size() < kMaxDiagnostics &&
            (ts_node_is_error(node) || ts_node_is_missing(node))
        ) {
            const TSPoint point = ts_node_start_point(node);
            diagnostics.push_back({
                "error",
                ts_node_is_missing(node)
                    ? std::string("Missing syntax: ") +
                        ts_node_type(node)
                    : std::string("Syntax error"),
                start,
                end,
                point.row + 1,
                utf16_column(mapping, start_byte, point.row) + 1
            });
        }

        const uint32_t children = ts_node_child_count(node);
        for (uint32_t offset = 0; offset < children; ++offset) {
            const uint32_t index = children - 1 - offset;
            stack.push_back(ts_node_child(node, index));
        }
    }

    std::sort(
        spans.begin(),
        spans.end(),
        [](const Span& left, const Span& right) {
            if (left.start != right.start) {
                return left.start < right.start;
            }
            if (left.end != right.end) {
                return left.end < right.end;
            }
            return left.kind < right.kind;
        }
    );
    spans.erase(
        std::unique(
            spans.begin(),
            spans.end(),
            [](const Span& left, const Span& right) {
                return left.start == right.start &&
                    left.end == right.end &&
                    left.kind == right.kind;
            }
        ),
        spans.end()
    );

    const std::string result = serialize(
        grammar,
        root,
        truncated,
        spans,
        symbols,
        diagnostics
    );
    ts_tree_delete(tree);
    return result;
}

}  // namespace nexora::syntax
