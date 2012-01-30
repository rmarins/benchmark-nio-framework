package com.rafaelmarins.bench.nio;

public class Constant {

    public static final int MIN_READ_BUFFER_SIZE = 64;
    public static final int INITIAL_READ_BUFFER_SIZE = 16384;
    public static final int MAX_READ_BUFFER_SIZE = 8 * 1024;
    public static final int THREAD_POOL_SIZE = 2;
    public static final int CHANNEL_MEMORY_LIMIT = MAX_READ_BUFFER_SIZE * 2;
    public static final long GLOBAL_MEMORY_LIMIT = Runtime.getRuntime().maxMemory() / 3;

	private Constant() {
	}

}
