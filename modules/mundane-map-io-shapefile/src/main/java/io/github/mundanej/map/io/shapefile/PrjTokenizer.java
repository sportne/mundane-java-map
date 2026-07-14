package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.SourceException;
import java.util.Map;

/** Fixed-capacity PRJ token tape with an integrated bounded WKT1 grammar recognizer. */
final class PrjTokenizer {
    static final byte IDENTIFIER = 1;
    static final byte STRING = 2;
    static final byte NUMBER = 3;
    static final byte OPEN = 4;
    static final byte CLOSE = 5;
    static final byte COMMA = 6;
    private static final int TOKEN_LIMIT = 512;
    private static final int DEPTH_LIMIT = 16;

    private final String source;
    private final byte[] input;
    private final int base;
    private final CancellationToken cancellation;
    private final byte[] kinds = new byte[TOKEN_LIMIT];
    private final int[] spans = new int[TOKEN_LIMIT * 2];
    private final byte[] grammarStack = new byte[DEPTH_LIMIT];
    private int tokenCount;
    private int depth;
    private boolean pendingIdentifier;
    private boolean rootStarted;
    private boolean rootComplete;

    PrjTokenizer(String source, byte[] input, int base, CancellationToken cancellation) {
        this.source = source;
        this.input = input;
        this.base = base;
        this.cancellation = cancellation;
    }

    void scan() {
        int offset = base;
        while (true) {
            while (offset < input.length && whitespace(input[offset] & 0xff)) {
                checkpoint(offset);
                offset++;
            }
            if (offset == input.length) {
                finish(offset);
                return;
            }
            int start = offset;
            int value = input[offset] & 0xff;
            byte kind;
            if (letter(value) || value == '_') {
                offset++;
                while (offset < input.length) {
                    int next = input[offset] & 0xff;
                    if (!letter(next) && !digit(next) && next != '_') {
                        break;
                    }
                    checkpoint(offset);
                    offset++;
                }
                kind = IDENTIFIER;
            } else if (value == '"') {
                offset = quoted(offset);
                kind = STRING;
            } else if (value == '+' || value == '-' || value == '.' || digit(value)) {
                offset = decimal(offset);
                kind = NUMBER;
            } else {
                kind =
                        switch (value) {
                            case '[' -> OPEN;
                            case ']' -> CLOSE;
                            case ',' -> COMMA;
                            default -> throw syntax(start, "syntax");
                        };
                offset++;
            }
            add(kind, start, offset);
            accept(kind, start);
        }
    }

    int tokenCount() {
        return tokenCount;
    }

    byte kind(int token) {
        return kinds[token];
    }

    int start(int token) {
        return spans[token * 2];
    }

    int end(int token) {
        return spans[token * 2 + 1];
    }

    byte[] input() {
        return input;
    }

    private int quoted(int quote) {
        int offset = quote + 1;
        while (offset < input.length) {
            checkpoint(offset);
            int value = input[offset] & 0xff;
            if (value == '"') {
                if (offset + 1 < input.length && input[offset + 1] == '"') {
                    offset += 2;
                    continue;
                }
                return offset + 1;
            }
            if (value <= 0x1f || value == 0x7f) {
                throw syntax(offset, "syntax");
            }
            offset++;
        }
        throw syntax(input.length, "syntax");
    }

    private int decimal(int start) {
        int offset = start;
        if (input[offset] == '+' || input[offset] == '-') {
            offset++;
            if (offset == input.length) {
                throw syntax(offset, "syntax");
            }
        }
        int integerDigits = 0;
        while (offset < input.length && digit(input[offset] & 0xff)) {
            checkpoint(offset);
            integerDigits++;
            offset++;
        }
        int fractionDigits = 0;
        if (offset < input.length && input[offset] == '.') {
            offset++;
            while (offset < input.length && digit(input[offset] & 0xff)) {
                checkpoint(offset);
                fractionDigits++;
                offset++;
            }
        }
        if (integerDigits == 0 && fractionDigits == 0) {
            throw syntax(offset, "syntax");
        }
        if (offset < input.length && (input[offset] == 'e' || input[offset] == 'E')) {
            offset++;
            if (offset < input.length && (input[offset] == '+' || input[offset] == '-')) {
                offset++;
            }
            int exponentStart = offset;
            while (offset < input.length && digit(input[offset] & 0xff)) {
                checkpoint(offset);
                offset++;
            }
            if (offset == exponentStart) {
                throw syntax(offset, "syntax");
            }
        }
        return offset;
    }

    private void add(byte kind, int start, int end) {
        if (tokenCount == TOKEN_LIMIT) {
            throw syntax(start, "tokens");
        }
        checkpoint(start);
        kinds[tokenCount] = kind;
        spans[tokenCount * 2] = start;
        spans[tokenCount * 2 + 1] = end;
        tokenCount++;
    }

    private void accept(byte kind, int start) {
        boolean again = true;
        while (again) {
            again = false;
            if (rootComplete) {
                throw syntax(start, "syntax");
            }
            if (!rootStarted) {
                if (kind != IDENTIFIER) {
                    throw syntax(start, "syntax");
                }
                rootStarted = true;
                pendingIdentifier = true;
                return;
            }
            if (pendingIdentifier) {
                if (kind == OPEN) {
                    if (depth == DEPTH_LIMIT) {
                        throw syntax(start, "nesting");
                    }
                    grammarStack[depth++] = 0;
                    pendingIdentifier = false;
                    return;
                }
                if (depth == 0 || grammarStack[depth - 1] == 1) {
                    throw syntax(start, "syntax");
                }
                grammarStack[depth - 1] = 1;
                pendingIdentifier = false;
                again = true;
                continue;
            }
            if (depth == 0) {
                throw syntax(start, "syntax");
            }
            byte state = grammarStack[depth - 1];
            if (state == 0 || state == 2) {
                if (kind == IDENTIFIER) {
                    pendingIdentifier = true;
                } else if (kind == STRING || kind == NUMBER) {
                    grammarStack[depth - 1] = 1;
                } else {
                    throw syntax(start, "syntax");
                }
                return;
            }
            if (kind == COMMA) {
                grammarStack[depth - 1] = 2;
                return;
            }
            if (kind == CLOSE) {
                depth--;
                if (depth == 0) {
                    rootComplete = true;
                } else {
                    grammarStack[depth - 1] = 1;
                }
                return;
            }
            throw syntax(start, "syntax");
        }
    }

    private void finish(int offset) {
        if (pendingIdentifier) {
            if (depth == 0 || grammarStack[depth - 1] == 1) {
                throw syntax(offset, "syntax");
            }
            grammarStack[depth - 1] = 1;
            pendingIdentifier = false;
        }
        if (!rootComplete || depth != 0) {
            throw syntax(offset, "syntax");
        }
    }

    private SourceException syntax(int offset, String reason) {
        return PrjReader.failure(source, "SHAPEFILE_PRJ_INVALID", offset, Map.of("reason", reason));
    }

    void checkpoint(int work) {
        if ((work & 4095) == 0) {
            Shapefiles.checkpoint(source, cancellation);
        }
    }

    void checkpoint() {
        Shapefiles.checkpoint(source, cancellation);
    }

    private static boolean whitespace(int value) {
        return value == 0x20 || (value >= 0x09 && value <= 0x0d);
    }

    private static boolean letter(int value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
    }

    private static boolean digit(int value) {
        return value >= '0' && value <= '9';
    }
}
