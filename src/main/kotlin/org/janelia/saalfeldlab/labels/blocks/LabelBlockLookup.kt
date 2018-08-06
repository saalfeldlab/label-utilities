package org.janelia.saalfeldlab.labels.blocks

import net.imglib2.Interval
import java.util.function.BiConsumer

interface LabelBlockLookup : java.util.function.Function<Long, Array<Interval>>, BiConsumer<Long, Array<Interval>>
