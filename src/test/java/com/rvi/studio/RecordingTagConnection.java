package com.rvi.studio;

import com.rvi.domain.NDefMessage;

import java.util.ArrayList;
import java.util.List;

final class RecordingTagConnection implements TagConnection
{
    private final List<String> calls = new ArrayList<>();
    private final String id;

    RecordingTagConnection(final String id)
    {
        this.id = id;
    }

    @Override
    public String readId()
    {
        calls.add("readId");
        return id;
    }

    @Override
    public void write(final NDefMessage message)
    {
        calls.add("write");
    }

    @Override
    public void close()
    {
        calls.add("close");
    }

    List<String> calls()
    {
        return List.copyOf(calls);
    }
}
