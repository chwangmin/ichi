package com.ichi.entry.service;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * 일기 본문 HTML 정화 (XSS 방지) + 텍스트 추출.
 *
 * 허용: 기본 서식 태그 + 인라인 이미지 <img data-ref="...">.
 * 이미지 src 는 본문에 넣지 않고 data-ref(Drive fileId)로만 참조하므로 src/href 는 막는다.
 * (script/style/on* 등은 전부 제거)
 */
@Component
public class HtmlSanitizer {

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
        .allowElements("p", "br", "b", "strong", "i", "em", "u", "s",
            "ul", "ol", "li", "blockquote", "h1", "h2", "h3", "div", "span", "img")
        // 인라인 이미지는 data-ref(Drive fileId)로만. 실제 src 는 프론트가 해석.
        .allowAttributes("data-ref").onElements("img")
        .toFactory();

    /** 저장 전 정화된 HTML 반환. */
    public String sanitize(String rawHtml) {
        if (rawHtml == null) {
            return "";
        }
        return POLICY.sanitize(rawHtml);
    }

    /**
     * HTML 에서 텍스트만 뽑아 미리보기 문자열 생성 (태그 제거 + 공백 정리 + 길이 제한).
     */
    public String toPreview(String html, int maxLen) {
        if (html == null) {
            return "";
        }
        String text = html
            .replaceAll("(?is)<br\\s*/?>", " ")
            .replaceAll("(?is)</(p|div|li|h1|h2|h3|blockquote)>", " ")
            .replaceAll("(?is)<[^>]+>", "");       // 남은 태그 제거
        text = decodeEntities(text)
            .replaceAll("\\s+", " ")
            .trim();
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen).trim() + "…";
    }

    /**
     * HTML 에서 태그를 제거해 줄바꿈을 보존한 순수 텍스트로 변환.
     * Drive 에서 사람이 바로 읽는 body.txt 용 (길이 제한 없음).
     */
    public String toPlainText(String html) {
        if (html == null) {
            return "";
        }
        String text = html
            .replaceAll("(?is)<br\\s*/?>", "\n")
            .replaceAll("(?is)</(p|div|li|h1|h2|h3|blockquote)>", "\n")
            .replaceAll("(?is)<[^>]+>", "");       // 남은 태그 제거(<img data-ref> 포함)
        text = decodeEntities(text)
            .replaceAll("[ \\t]+", " ")             // 가로 공백만 정리(줄바꿈 보존)
            .replaceAll("\\n{3,}", "\n\n")          // 빈 줄 과다 정리
            .replaceAll("(?m)[ \\t]+$", "")         // 줄 끝 공백 제거
            .trim();
        return text;
    }

    /**
     * 텍스트 추출 후 남은 HTML 엔티티를 실제 문자로 되돌린다.
     * OWASP sanitizer 는 본문 텍스트의 " ' < > & 를 각각 &#34; &#39; &lt; &gt; &amp; 로
     * 이스케이프하므로, 사람이 읽는 preview/txt 에서는 원래 문자로 풀어줘야 한다.
     * &amp; 는 다른 엔티티 복원 뒤 마지막에 풀어 이중 디코딩을 막는다.
     */
    private String decodeEntities(String text) {
        return text
            .replace("&nbsp;", " ")
            .replace("&#34;", "\"").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&");
    }
}
