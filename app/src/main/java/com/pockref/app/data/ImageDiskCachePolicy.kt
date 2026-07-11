package com.pockref.app.data

internal object ImageDiskCachePolicy {

    /**
     * The original level has no bounded pixel cost, so it stays memory-only.
     * Every decode-ladder level has a known maximum edge and may be reused
     * from disk on a later scene open.
     */
    fun shouldCache(decodeLevel: Int): Boolean =
        decodeLevel in SceneImageDecoder.DECODE_LADDER.indices
}
