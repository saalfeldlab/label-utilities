package org.janelia.saalfeldlab.labels.blocks

import net.imglib2.cache.Invalidate

interface CachedLabelBlockLookup : LabelBlockLookup, Invalidate<LabelBlockLookupKey>
