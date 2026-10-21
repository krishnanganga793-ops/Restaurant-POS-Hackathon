package com.restaurantpos.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Saves, previews, or prints the receipt text currently displayed on a screen. */
final class ReceiptViewSupport {
    private ReceiptViewSupport() {
    }

    static void save(Stage stage, TextArea receiptArea) {
        if (receiptArea.getText().isBlank()) {
            showAlert(Alert.AlertType.INFORMATION, "No receipt", "Select an order first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save receipt");
        chooser.setInitialFileName("restaurant-receipt.txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text files", "*.txt"));
        var file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), receiptArea.getText());
            showAlert(Alert.AlertType.INFORMATION, "Receipt saved", "The receipt was saved successfully.");
        } catch (IOException | SecurityException exception) {
            showAlert(Alert.AlertType.ERROR, "Could not save receipt", "Check the selected file location and try again.");
        }
    }

    static void print(Stage stage, TextArea receiptArea) {
        String receipt = receiptArea.getText();
        if (receipt.isBlank()) {
            showAlert(Alert.AlertType.INFORMATION, "No receipt", "Select an order first.");
            return;
        }

        ButtonType printButton = new ButtonType("Continue to Print", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> previewDialog = new Dialog<>();
        previewDialog.initOwner(stage);
        previewDialog.setTitle("Receipt Preview");
        previewDialog.setHeaderText("Review the receipt before printing");
        previewDialog.getDialogPane().getButtonTypes().addAll(printButton, ButtonType.CANCEL);
        TextArea preview = new TextArea(receipt);
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
        preview.setPrefSize(520, 560);
        previewDialog.getDialogPane().setContent(preview);
        Optional<ButtonType> choice = previewDialog.showAndWait();
        if (choice.isEmpty() || choice.get() != printButton) {
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            showAlert(Alert.AlertType.ERROR, "Printing unavailable", "No printer is available.");
            return;
        }
        try {
            if (!job.showPrintDialog(stage)) {
                job.cancelJob();
                return;
            }

            PageLayout layout = job.getJobSettings().getPageLayout();
            Font font = Font.font("Monospaced", 9);
            List<String> lines = wrapLines(receipt, layout.getPrintableWidth() - 20, font);
            int linesPerPage = Math.max(1,
                (int) (layout.getPrintableHeight() / (font.getSize() * 1.45)));

            for (int start = 0; start < lines.size(); start += linesPerPage) {
                int end = Math.min(start + linesPerPage, lines.size());
                Text text = new Text(String.join("\n", lines.subList(start, end)));
                text.setFont(font);
                TextFlow printablePage = new TextFlow(text);
                printablePage.setPadding(new Insets(8));
                printablePage.setPrefWidth(layout.getPrintableWidth());
                printablePage.setMinWidth(layout.getPrintableWidth());
                printablePage.setMaxWidth(layout.getPrintableWidth());
                printablePage.resize(layout.getPrintableWidth(), layout.getPrintableHeight());
                printablePage.applyCss();
                printablePage.layout();
                if (!job.printPage(layout, printablePage)) {
                    job.cancelJob();
                    showAlert(Alert.AlertType.ERROR, "Print failed", "The receipt could not be sent to the printer.");
                    return;
                }
            }
            if (!job.endJob()) {
                showAlert(Alert.AlertType.ERROR, "Print failed", "The printer could not finish the receipt.");
            }
        } catch (RuntimeException exception) {
            job.cancelJob();
            showAlert(Alert.AlertType.ERROR, "Print failed", "The receipt could not be printed.");
        }
    }

    private static List<String> wrapLines(String receipt, double printableWidth, Font font) {
        Text sample = new Text("M");
        sample.setFont(font);
        int maxCharacters = Math.max(16,
            (int) (printableWidth / sample.getLayoutBounds().getWidth()));
        List<String> lines = new ArrayList<>();
        for (String originalLine : receipt.split("\\R", -1)) {
            String remaining = originalLine;
            if (remaining.isEmpty()) {
                lines.add("");
                continue;
            }
            while (remaining.length() > maxCharacters) {
                int splitAt = remaining.lastIndexOf(' ', maxCharacters);
                if (splitAt < 1) {
                    splitAt = maxCharacters;
                }
                lines.add(remaining.substring(0, splitAt));
                remaining = remaining.substring(splitAt).stripLeading();
            }
            lines.add(remaining);
        }
        return lines;
    }

    static void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
