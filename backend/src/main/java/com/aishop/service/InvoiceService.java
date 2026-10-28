package com.aishop.service;

import com.aishop.config.AppProperties;
import com.aishop.entity.Invoice;
import com.aishop.entity.Order;
import com.aishop.entity.OrderItem;
import com.aishop.repository.InvoiceRepository;
import com.aishop.repository.OrderRepository;
import org.openpdf.text.*;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Generates and stores a PDF invoice automatically for every order whose payment is confirmed. */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter INVOICE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final Color BRAND = new Color(79, 70, 229);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color HEADER_BG = new Color(238, 242, 255);

    private final InvoiceRepository invoices;
    private final OrderRepository orders;
    private final AppProperties.Store store;

    public InvoiceService(InvoiceRepository invoices, OrderRepository orders, AppProperties props) {
        this.invoices = invoices;
        this.orders = orders;
        this.store = props.store();
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderPaid(OrderPaidEvent event) {
        if (invoices.existsByOrderId(event.orderId())) {
            return;
        }
        Order order = orders.findWithItemsById(event.orderId()).orElseThrow();
        String number = invoiceNumber(order);
        invoices.save(new Invoice(order, number, render(order, number), store.name()));
        log.info("Generated invoice {} for order {}", number, order.getId());
    }

    /** On startup, re-render invoices printed under a previous store name so downloads match the current brand. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void refreshOutdatedInvoices() {
        List<Invoice> outdated = invoices.findByStoreNameIsNullOrStoreNameNot(store.name());
        for (Invoice invoice : outdated) {
            Order order = orders.findWithItemsById(invoice.getOrder().getId()).orElseThrow();
            invoice.rerender(render(order, invoice.getInvoiceNumber()), store.name());
        }
        if (!outdated.isEmpty()) {
            log.info("Re-rendered {} invoice(s) with store name {}", outdated.size(), store.name());
        }
    }

    static String invoiceNumber(Order order) {
        return "INV-" + order.getPaidAt().atZone(IST).format(INVOICE_DATE)
                + "-" + String.format("%06d", order.getId());
    }

    byte[] render(Order order, String number) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, BRAND);
        Font h = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
        Font muted = FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);

        // Header: store on the left, invoice meta on the right.
        PdfPTable header = table(new float[]{3, 2});
        PdfPCell storeCell = plain();
        storeCell.addElement(new Paragraph(store.name(), title));
        storeCell.addElement(new Paragraph(store.address(), muted));
        storeCell.addElement(new Paragraph("GSTIN: " + store.gstin() + "  |  " + store.supportEmail(), muted));
        header.addCell(storeCell);
        PdfPCell meta = plain();
        meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        meta.addElement(right(new Paragraph("TAX INVOICE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK))));
        meta.addElement(right(new Paragraph("Invoice #: " + number, body)));
        meta.addElement(right(new Paragraph("Order #: " + order.getId(), body)));
        meta.addElement(right(new Paragraph("Date: " + order.getPaidAt().atZone(IST).format(DATE), body)));
        header.addCell(meta);
        doc.add(header);
        doc.add(spacer(14));

        // Billing and payment details.
        PdfPTable parties = table(new float[]{1, 1});
        PdfPCell billTo = plain();
        billTo.addElement(new Paragraph("BILLED TO", h));
        billTo.addElement(new Paragraph(order.getShippingName(), body));
        billTo.addElement(new Paragraph(order.getShippingAddress(), body));
        billTo.addElement(new Paragraph("Phone: " + order.getShippingPhone(), body));
        billTo.addElement(new Paragraph(order.getUser().getEmail(), body));
        parties.addCell(billTo);
        PdfPCell payment = plain();
        payment.addElement(new Paragraph("PAYMENT", h));
        payment.addElement(new Paragraph("Method: Razorpay", body));
        payment.addElement(new Paragraph("Payment ID: " + order.getRazorpayPaymentId(), body));
        payment.addElement(new Paragraph("Gateway order: " + order.getRazorpayOrderId(), body));
        payment.addElement(new Paragraph("Status: PAID", body));
        parties.addCell(payment);
        doc.add(parties);
        doc.add(spacer(16));

        // Line items.
        PdfPTable items = new PdfPTable(new float[]{0.5f, 4.5f, 1, 1.6f, 1.8f});
        items.setWidthPercentage(100);
        for (String col : new String[]{"#", "Item", "Qty", "Unit price", "Amount"}) {
            PdfPCell c = new PdfPCell(new Phrase(col, h));
            c.setBackgroundColor(HEADER_BG);
            c.setPadding(7);
            c.setBorderColor(HEADER_BG);
            if (!col.equals("Item") && !col.equals("#")) {
                c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            items.addCell(c);
        }
        int n = 1;
        for (OrderItem item : order.getItems()) {
            items.addCell(row(String.valueOf(n++), body, Element.ALIGN_LEFT));
            items.addCell(row(item.getProductName(), body, Element.ALIGN_LEFT));
            items.addCell(row(String.valueOf(item.getQuantity()), body, Element.ALIGN_RIGHT));
            items.addCell(row(money(item.getUnitPrice()), body, Element.ALIGN_RIGHT));
            items.addCell(row(money(item.getLineTotal()), body, Element.ALIGN_RIGHT));
        }
        doc.add(items);
        doc.add(spacer(10));

        // Totals. Prices are GST-inclusive, so show the tax component that is already in the total.
        BigDecimal total = order.getTotalAmount();
        BigDecimal taxable = total.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(118), 2, java.math.RoundingMode.HALF_UP);
        PdfPTable totals = new PdfPTable(new float[]{3, 2});
        totals.setWidthPercentage(60);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.addCell(totalCell("Taxable value", body, Element.ALIGN_LEFT));
        totals.addCell(totalCell(money(taxable), body, Element.ALIGN_RIGHT));
        totals.addCell(totalCell("GST @ 18% (included)", body, Element.ALIGN_LEFT));
        totals.addCell(totalCell(money(total.subtract(taxable)), body, Element.ALIGN_RIGHT));
        totals.addCell(totalCell("Shipping", body, Element.ALIGN_LEFT));
        totals.addCell(totalCell("FREE", body, Element.ALIGN_RIGHT));
        PdfPCell grandLabel = totalCell("Total paid", bold, Element.ALIGN_LEFT);
        PdfPCell grandValue = totalCell(money(total), bold, Element.ALIGN_RIGHT);
        grandLabel.setBorder(Rectangle.TOP);
        grandValue.setBorder(Rectangle.TOP);
        totals.addCell(grandLabel);
        totals.addCell(grandValue);
        doc.add(totals);

        doc.add(spacer(30));
        Paragraph thanks = new Paragraph("Thank you for shopping with " + store.name() + "!", h);
        thanks.setAlignment(Element.ALIGN_CENTER);
        doc.add(thanks);
        Paragraph note = new Paragraph("This is a computer-generated invoice and does not require a signature.", muted);
        note.setAlignment(Element.ALIGN_CENTER);
        doc.add(note);

        doc.close();
        return out.toByteArray();
    }

    private static String money(BigDecimal amount) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.of("en", "IN"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "INR " + f.format(amount);
    }

    private static PdfPTable table(float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        return t;
    }

    private static PdfPCell plain() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static PdfPCell row(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setPadding(7);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(new Color(226, 232, 240));
        c.setHorizontalAlignment(align);
        return c;
    }

    private static PdfPCell totalCell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(5);
        c.setHorizontalAlignment(align);
        return c;
    }

    private static Paragraph right(Paragraph p) {
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(height);
        return p;
    }
}
