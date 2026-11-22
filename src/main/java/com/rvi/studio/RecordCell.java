package com.rvi.studio;

import com.rvi.domain.NDefContact;
import com.rvi.domain.NDefContent;
import com.rvi.domain.NDefText;
import com.rvi.domain.NDefUri;
import com.rvi.domain.NDefWifi;
import javafx.beans.InvalidationListener;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;

final class RecordCell
{
    private final ListCell<NDefContent> view = new ListCell<>();
    private final Label type = new Label();
    private final Label detail = new Label();
    private final VBox row = new VBox(3, type, detail);

    RecordCell()
    {
        final InvalidationListener refresh = property -> refresh();

        type.getStyleClass().add("record-type");
        detail.getStyleClass().add("record-detail");
        detail.setMaxWidth(Double.MAX_VALUE);

        row.prefWidthProperty().bind(view.widthProperty().subtract(36));

        view.itemProperty().addListener(refresh);
        view.emptyProperty().addListener(refresh);
    }

    ListCell<NDefContent> view()
    {
        return view;
    }

    private void refresh()
    {
        final NDefContent item = view.getItem();
        final String title;
        final String description;

        if (view.isEmpty() || item == null)
        {
            view.setGraphic(null);
            view.setAccessibleText(null);
            return;
        }

        title = RecordKind.fromContent(item).toString();
        description = switch (item)
        {
            case NDefUri uri -> uri.uri();
            case NDefText text -> text.text().lines().findFirst().orElse("");
            case NDefContact contact -> (contact.firstName() + " " + contact.lastName()).strip();
            case NDefWifi wifi -> wifi.ssid();
        };

        type.setText(title);
        detail.setText(description);
        view.setGraphic(row);
        view.setAccessibleText(title + ", " + description);
    }
}
