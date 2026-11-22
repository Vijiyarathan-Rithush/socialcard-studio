package com.rvi.studio;

import com.rvi.domain.NDefContact;
import com.rvi.domain.NDefContent;
import com.rvi.domain.NDefText;
import com.rvi.domain.NDefUri;
import com.rvi.domain.NDefWifi;

enum RecordKind
{
    LINK("Link"),
    TEXT("Text"),
    CONTACT("Contact"),
    WIFI("Wi-Fi");

    private final String title;

    RecordKind(final String title)
    {
        this.title = title;
    }

    static RecordKind fromContent(final NDefContent content)
    {
        return switch (content)
        {
            case NDefUri uri -> LINK;
            case NDefText text -> TEXT;
            case NDefContact contact -> CONTACT;
            case NDefWifi wifi -> WIFI;
        };
    }

    @Override
    public String toString()
    {
        return title;
    }
}
