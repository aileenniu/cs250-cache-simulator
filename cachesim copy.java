import java.io.*;
import java.util.*;

public class cachesim {

    static class Frame {
        byte[] block;
        int tag;
        boolean valid;
        boolean dirty; 
    
        public Frame(byte[] block, int tag, boolean valid, boolean dirty) {
            this.block = block;
            this.tag = tag;
            this.valid = valid;
            this.dirty = dirty;
        }
    }

    static Scanner traceFileScanner;

    // Struct describing an access from the trace file. Returned by traceNextAccess.
    private static class CacheAccess {

        boolean isStore;
        int address;
        int accessSize;
        byte data[];
    }

    /**
     * Opens a trace file, given its name. Must be called before traceNextAccess,
     * which will begin reading from this file.
     * @param filename: the name of the trace file to open
     */
    public static void traceInit(String filename) {
        try {
            traceFileScanner = new Scanner(new File(filename));
        } catch (FileNotFoundException e) {
            System.err.println("Failed to open trace file: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Checks if we've already read all accesses from the trace file.
     * @return true if the trace file is complete, false if there's more to read.
     */
    public static boolean traceFinished() {
        return !traceFileScanner.hasNextLine();
    }

    /**
     * Read the next access in the trace. Errors if traceFinished() == true.
     * @return The access as a cacheAccess struct.
     */
    public static CacheAccess traceNextAccess() {
        String[] parts = traceFileScanner.nextLine().strip().split("\\s+");
        CacheAccess result = new CacheAccess();

        // Parse address and access size
        result.address = Integer.parseInt(parts[1].substring(2), 16);
        result.accessSize = Integer.parseInt(parts[2]);

        // Check access type
        if (parts[0].equals("store")) {
            result.isStore = true;

            // Read data
            result.data = new byte[result.accessSize];
            for (int i = 0; i < result.accessSize; i++) {
                result.data[i] = (byte) Integer.parseInt(
                    parts[3].substring(i * 2, 2 + i * 2),
                    16
                );
            }
        } else if (parts[0].equals("load")) {
            result.isStore = false;
        } else {
            System.err.println("Invalid trace file access type" + parts[0]);
            System.exit(1); 
        }
        return result;
    }
    // Your code can go here (or anywhere in this class), including a public static void main method.

    private static int log2(int num) {
        int log = 0;
        while (num > 1) {
            num /= 2; 
            log++;   
        }
        return log;
    }

    private static int ones(int a) {
        return ((1 << a)-1);
    }

    public static String bytesToHex(byte[] data) {
        String[] hex = new String[data.length];
        for (int i=0; i<data.length; i++) {
            hex[i] = String.format("%02x", data[i]);
        }
        return String.join("", hex);
    }

    public static void main(String[] args) {
        // Initialize main memory
        byte[] mainMem = new byte[16777216];

        // Parse arguments 
        String traceFile = args[0];
        int cacheSizeKB = Integer.parseInt(args[1]);
        int associativity = Integer.parseInt(args[2]);
        int blockSize = Integer.parseInt(args[3]);
        traceInit(traceFile); 

        // Cache math 
        int cacheSizeB = cacheSizeKB * 1024; 
        int numFrames = cacheSizeB / blockSize;
        int numSets = numFrames / associativity;
        int offsetBits = log2(blockSize);
        int indexBits = log2(numSets);

        // Create cache 
        ArrayList<Queue<Frame>> cache = new ArrayList<>(numSets); 
        for (int i = 0; i < numSets; i++) { 
            cache.add(new LinkedList<>());
        }

        boolean isFirstAccess = true;

        while (!traceFinished()) {
            CacheAccess access = traceNextAccess();
            boolean isStore = access.isStore;
            int address = access.address;
            int accessSize = access.accessSize;
            byte[] data = access.data;

            // Address math 
            int blockOffset = address & ones(offsetBits);
            int index = (address >> offsetBits) & ones(indexBits);
            int tag = address >> (indexBits + offsetBits);
            byte[] loadedData = new byte[accessSize];

            // Check cache for hit or miss
            boolean hit = false; 
            Queue<Frame> currQueue = cache.get(index);
            Frame targetFrame = null;

            for (Frame frame : currQueue) {
                if (frame.tag == tag && frame.valid) {
                    hit = true;
                    targetFrame = frame;
                    break;
                }
            }

            if (!hit) { 
                if (currQueue.size() >= associativity) { 
                    Frame evictedFrame = currQueue.poll();  
                    int evictedAddress = (evictedFrame.tag << (indexBits + offsetBits)) + (index << offsetBits);
                    if (evictedFrame.dirty) { 
                        for (int i = 0; i < blockSize; i++) {
                            mainMem[evictedAddress + i] = evictedFrame.block[i];
                        }
                        if (!isFirstAccess) {
                            System.out.printf("replacement 0x%s dirty\n", Integer.toHexString(evictedAddress));
                        }
                    } else {
                        if (!isFirstAccess) {
                            System.out.printf("replacement 0x%s clean\n", Integer.toHexString(evictedAddress));
                        }
                    }
                }

                byte[] newBlock = new byte[blockSize];
                int blockAddress = (tag << (indexBits + offsetBits)) | (index << offsetBits);
                Frame newFrame = new Frame(newBlock, tag, true, isStore);
                for (int i = 0; i < blockSize; i++) {
                    newFrame.block[i] = mainMem[blockAddress + i];
                }
                currQueue.add(newFrame);

                if (isStore) {
                    for (int i = 0; i < accessSize; i++) {
                        newFrame.block[blockOffset + i] = data[i];
                    }
                } else {
                    for (int i = 0; i < accessSize; i++) {
                        loadedData[i] = newFrame.block[blockOffset + i];
                    }
                }
            } else {
                if (isStore) {
                    targetFrame.dirty = true; 
                    for (int i = 0; i < accessSize; i++) {
                        targetFrame.block[blockOffset + i] = data[i];
                    }
                } else {
                    for (int i = 0; i < accessSize; i++) {
                        loadedData[i] = targetFrame.block[blockOffset + i];
                    }
                }
            }

            if (isStore) {
                System.out.printf("store 0x%s %s\n", Integer.toHexString(address), hit ? "hit" : "miss");
            } else {
                System.out.printf("load 0x%s %s %s\n", Integer.toHexString(address), hit ? "hit" : "miss", bytesToHex(loadedData));
            }
            isFirstAccess = false;
        } 
        System.exit(0);
    }
}