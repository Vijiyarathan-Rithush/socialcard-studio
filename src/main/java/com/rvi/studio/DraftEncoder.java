package com.rvi.studio;

import com.rvi.application.TlvEncoder;
import com.rvi.application.creator.NDefMessageCreator;
import com.rvi.domain.NDefContent;
import com.rvi.domain.NDefMessage;

import java.util.List;

final class DraftEncoder
{
    DraftEncoding encode(final List<NDefContent> records)
    {
        final NDefMessageCreator creator = new NDefMessageCreator();
        final NDefMessage message;
        final int bytes;

        records.forEach(creator::add);
        message = creator.build();
        bytes = new TlvEncoder().encode(message).length;

        return new DraftEncoding(message, bytes);
    }
}
