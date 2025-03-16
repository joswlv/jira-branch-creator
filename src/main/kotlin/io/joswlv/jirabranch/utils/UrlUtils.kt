package io.joswlv.jirabranch.utils

import com.intellij.openapi.diagnostic.Logger
import java.net.URI
import java.net.URISyntaxException

/**
 * URL 관련 유틸리티 기능을 제공하는 클래스
 */
object UrlUtils {
    private val LOG = Logger.getInstance(UrlUtils::class.java)

    /**
     * URL 문자열의 유효성을 검사합니다
     * @param url 검사할 URL 문자열
     * @return URL이 유효하면 true, 그렇지 않으면 false
     */
    fun validateUrl(url: String): Boolean {
        if (url.isBlank()) return false

        return try {
            val trimmedUrl = url.trim().replace(" ", "")
            // URI를 생성하여 URL 형식 유효성 검사
            URI(trimmedUrl)
            true
        } catch (e: URISyntaxException) {
            LOG.warn("유효하지 않은 URL 형식: $url, 오류: ${e.message}")
            false
        } catch (e: Exception) {
            LOG.warn("URL 유효성 검사 중 예상치 못한 오류 발생: ${e.message}")
            false
        }
    }

    /**
     * URL 문자열을 정리하고 안전하게 만듭니다
     * @param url 정리할 URL 문자열
     * @return 정리된 URL 문자열
     */
    fun sanitizeUrl(url: String): String {
        // 공백 제거 및 후행 슬래시 제거
        var sanitized = url.trim().replace(" ", "")

        // URL 끝의 슬래시 제거 (필요한 경우에만)
        if (sanitized.endsWith("/")) {
            sanitized = sanitized.substringBeforeLast("/")
        }

        return sanitized
    }

    /**
     * URL 문자열을 디버깅합니다
     * @param url 디버깅할 URL 문자열
     */
    fun debugUrl(url: String) {
        LOG.info("URL 길이: ${url.length}")
        LOG.info("URL 문자: ${url.toCharArray().joinToString(" ") { "0x${it.code.toString(16)}" }}")

        // 일반적인 문제 확인
        if (url.contains(" ")) {
            LOG.warn("URL에 공백이 포함되어 있습니다. 위치: ${url.indices.filter { url[it] == ' ' }}")
        }

        // URI 생성 테스트
        try {
            val uri = URI(url)
            LOG.info("URI 직접 생성 성공: $uri")
        } catch (e: Exception) {
            LOG.error("URI 직접 생성 실패: ${e.message}")
        }

        try {
            val cleanUrl = url.trim().replace(" ", "")
            val uri = URI(cleanUrl)
            LOG.info("정리 후 URI 생성 성공: $uri")
        } catch (e: Exception) {
            LOG.error("정리 후 URI 생성 실패: ${e.message}")
        }
    }
}