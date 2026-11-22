package com.rvi.studio;

import com.rvi.application.IReaderListener;

import javax.smartcardio.CardTerminal;
import java.util.function.Consumer;

final class ReaderStatusListener implements IReaderListener
{
    private final Consumer<Boolean> onPresenceChanged;
    private final Consumer<Exception> onFailure;

    ReaderStatusListener(final Consumer<Boolean> onPresenceChanged, final Consumer<Exception> onFailure)
    {
        this.onPresenceChanged = onPresenceChanged;
        this.onFailure = onFailure;
    }

    @Override
    public void onCardInserted(final CardTerminal reader)
    {
        onPresenceChanged.accept(true);
    }

    @Override
    public void onCardRemoved(final CardTerminal reader)
    {
        onPresenceChanged.accept(false);
    }

    @Override
    public void onError(final Exception error)
    {
        onFailure.accept(error);
    }
}
