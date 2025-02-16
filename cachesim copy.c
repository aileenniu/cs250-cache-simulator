#include <assert.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdbool.h>
#include <string.h>

// C doesn't separate "characters" from "bytes." To clarify what's a string
// (i.e. ASCII bytes that can be printed) and what's just an array of bytes,
// we define a new "byte" type, which internally is just an (unsigned) char.
typedef unsigned char byte;

// Struct describing an access from the trace file. Returned by `traceNextAccess`.
typedef struct {
    bool isStore;
    int address;
    int accessSize;
    byte data[8];
} cacheAccess;

// The trace file being read. Set by `traceInit`.
FILE * traceFile = NULL;

// The last line read from the trace file. Shouldn't need to access directly,
// this is used internally by `traceFinished` and `traceNextAccess`.
char traceLineBuffer[64] = {0};

/**
 * Opens a trace file, given its name. Must be called before `traceNextAccess`,
 * which will begin reading from this file.
 * @param filename: the name of the trace file to open
 */
void traceInit(char * filename) {
    assert(traceFile == NULL); // Must not initialize more than once
    assert(filename != NULL); // Filename must be set.
    traceFile = fopen(filename, "r");
    if (!traceFile) {
        fprintf(stderr, "Failed to open trace file: %s\n", filename);
        exit(EXIT_FAILURE);
    }
}

/**
 * Checks if we've already read all accesses from the trace file.
 * @return true if the trace file is complete, false if there's more to read.
 */
bool traceFinished() {
    assert(traceFile != NULL); // Must have initialized the file with `traceInit`
    if (traceLineBuffer[0]) {
        // If there's a line buffered, it can be read.
        return false;
    }
    // Otherwise, read a new line into the buffer, and return `true` if the read fails.
    return fgets(traceLineBuffer, 64, traceFile) == NULL;
}

/**
 * Read the next access in the trace. Errors if `traceFinished() == true`.
 * @return The access as a `cacheAccess` struct.
 */
cacheAccess traceNextAccess() {
    // Error if the trace is finished. This will also populate the line buffer,
    // if it's not already full.
    assert(!traceFinished());
    char accessType[16];
    cacheAccess result;
    int charsRead;
    sscanf(traceLineBuffer, "%15s 0x%x %d %n", accessType, &result.address, &result.accessSize, &charsRead);

    // Check access type
    if (strcmp(accessType, "store") == 0) {
        result.isStore = true;

        // Load data bytes
        for (int i = 0; i < result.accessSize; i++) {
            sscanf(traceLineBuffer + charsRead + 2 * i, "%02hhx", &result.data[i]);
        }
    } else if (strcmp(accessType, "load") == 0) {
        result.isStore = false;
    } else {
        fprintf(stderr, "Invalid trace file access type: %s\n", accessType);
        exit(1);
    }

    // Zero line buffer to stop future accesses
    memset(traceLineBuffer, 0, sizeof(traceLineBuffer));
    return result;
}

// Your code goes here, including an `int main` method.
