package dev.junitmanager.discover;

import java.util.Locale;
import java.util.Set;

final class XmlSnips {
    private XmlSnips() {
    }

    static String stripBlocks(String xml, Set<String> names) {
        StringBuilder out = new StringBuilder(xml.length());
        int i = 0;
        while (i < xml.length()) {
            if (xml.charAt(i) != '<') {
                out.append(xml.charAt(i));
                i++;
                continue;
            }
            if (xml.startsWith("<!--", i)) {
                int end = xml.indexOf("-->", i + 4);
                if (end < 0) {
                    break;
                }
                out.append(xml, i, end + 3);
                i = end + 3;
                continue;
            }
            if (xml.startsWith("<?", i) || xml.startsWith("<!", i)) {
                int end = xml.indexOf('>', i);
                if (end < 0) {
                    break;
                }
                out.append(xml, i, end + 1);
                i = end + 1;
                continue;
            }
            boolean closing = xml.startsWith("</", i);
            int nameStart = i + (closing ? 2 : 1);
            int nameEnd = nameStart;
            while (nameEnd < xml.length()) {
                char c = xml.charAt(nameEnd);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':' || c == '.') {
                    nameEnd++;
                } else {
                    break;
                }
            }
            String rawName = xml.substring(nameStart, nameEnd);
            int colon = rawName.indexOf(':');
            String local = (colon >= 0 ? rawName.substring(colon + 1) : rawName).toLowerCase(Locale.ROOT);
            int tagEnd = endOfTag(xml, nameEnd);
            if (tagEnd < 0) {
                break;
            }
            boolean selfClosing = tagEnd >= 2 && xml.charAt(tagEnd - 2) == '/';
            if (!closing && !selfClosing && names.contains(local)) {
                i = skipElement(xml, local, tagEnd);
                continue;
            }
            out.append(xml, i, tagEnd);
            i = tagEnd;
        }
        return out.toString();
    }

    static String inner(String xml, String element) {
        String wanted = element.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < xml.length()) {
            int lt = xml.indexOf('<', i);
            if (lt < 0) {
                return "";
            }
            if (xml.startsWith("<!--", lt)) {
                int end = xml.indexOf("-->", lt + 4);
                i = end < 0 ? xml.length() : end + 3;
                continue;
            }
            if (xml.startsWith("</", lt) || xml.startsWith("<?", lt) || xml.startsWith("<!", lt)) {
                i = lt + 1;
                continue;
            }
            int nameStart = lt + 1;
            int nameEnd = nameStart;
            while (nameEnd < xml.length()) {
                char c = xml.charAt(nameEnd);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':' || c == '.') {
                    nameEnd++;
                } else {
                    break;
                }
            }
            String rawName = xml.substring(nameStart, nameEnd);
            int colon = rawName.indexOf(':');
            String local = (colon >= 0 ? rawName.substring(colon + 1) : rawName).toLowerCase(Locale.ROOT);
            int tagEnd = endOfTag(xml, nameEnd);
            if (tagEnd < 0) {
                return "";
            }
            boolean selfClosing = tagEnd >= 2 && xml.charAt(tagEnd - 2) == '/';
            if (local.equals(wanted)) {
                if (selfClosing) {
                    return "";
                }
                int close = indexOfClose(xml, wanted, tagEnd);
                if (close < 0) {
                    return "";
                }
                return xml.substring(tagEnd, close);
            }
            i = tagEnd;
        }
        return "";
    }

    private static int skipElement(String xml, String localName, int contentStart) {
        int depth = 1;
        int i = contentStart;
        while (i < xml.length() && depth > 0) {
            int lt = xml.indexOf('<', i);
            if (lt < 0) {
                return xml.length();
            }
            if (xml.startsWith("<!--", lt)) {
                int end = xml.indexOf("-->", lt + 4);
                i = end < 0 ? xml.length() : end + 3;
                continue;
            }
            boolean closing = xml.startsWith("</", lt);
            if (xml.startsWith("<?", lt) || xml.startsWith("<!", lt)) {
                int end = xml.indexOf('>', lt);
                i = end < 0 ? xml.length() : end + 1;
                continue;
            }
            int nameStart = lt + (closing ? 2 : 1);
            int nameEnd = nameStart;
            while (nameEnd < xml.length()) {
                char c = xml.charAt(nameEnd);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':' || c == '.') {
                    nameEnd++;
                } else {
                    break;
                }
            }
            String rawName = xml.substring(nameStart, nameEnd);
            int colon = rawName.indexOf(':');
            String local = (colon >= 0 ? rawName.substring(colon + 1) : rawName).toLowerCase(Locale.ROOT);
            int tagEnd = endOfTag(xml, nameEnd);
            if (tagEnd < 0) {
                return xml.length();
            }
            boolean selfClosing = !closing && tagEnd >= 2 && xml.charAt(tagEnd - 2) == '/';
            if (local.equals(localName) && !selfClosing) {
                depth += closing ? -1 : 1;
            }
            i = tagEnd;
        }
        return i;
    }

    private static int indexOfClose(String xml, String localName, int contentStart) {
        int depth = 1;
        int i = contentStart;
        while (i < xml.length() && depth > 0) {
            int lt = xml.indexOf('<', i);
            if (lt < 0) {
                return -1;
            }
            if (xml.startsWith("<!--", lt)) {
                int end = xml.indexOf("-->", lt + 4);
                i = end < 0 ? xml.length() : end + 3;
                continue;
            }
            boolean closing = xml.startsWith("</", lt);
            if (xml.startsWith("<?", lt) || xml.startsWith("<!", lt)) {
                int end = xml.indexOf('>', lt);
                i = end < 0 ? xml.length() : end + 1;
                continue;
            }
            int nameStart = lt + (closing ? 2 : 1);
            int nameEnd = nameStart;
            while (nameEnd < xml.length()) {
                char c = xml.charAt(nameEnd);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':' || c == '.') {
                    nameEnd++;
                } else {
                    break;
                }
            }
            String rawName = xml.substring(nameStart, nameEnd);
            int colon = rawName.indexOf(':');
            String local = (colon >= 0 ? rawName.substring(colon + 1) : rawName).toLowerCase(Locale.ROOT);
            int tagEnd = endOfTag(xml, nameEnd);
            if (tagEnd < 0) {
                return -1;
            }
            boolean selfClosing = !closing && tagEnd >= 2 && xml.charAt(tagEnd - 2) == '/';
            if (local.equals(localName) && !selfClosing) {
                if (closing) {
                    depth--;
                    if (depth == 0) {
                        return lt;
                    }
                } else {
                    depth++;
                }
            }
            i = tagEnd;
        }
        return -1;
    }

    private static int endOfTag(String xml, int from) {
        boolean quote = false;
        char q = 0;
        for (int i = from; i < xml.length(); i++) {
            char c = xml.charAt(i);
            if (quote) {
                if (c == q) {
                    quote = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = true;
                q = c;
                continue;
            }
            if (c == '>') {
                return i + 1;
            }
        }
        return -1;
    }
}
