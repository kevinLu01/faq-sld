package com.sld.faq.infrastructure.wecom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sld.faq.common.BusinessException;
import com.sld.faq.config.properties.WeComProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 企业微信 OAuth 2.0 服务
 * <p>
 * 封装企业微信 OAuth 授权流程，包括：
 * <ol>
 *   <li>生成授权 URL（含 state 防 CSRF）</li>
 *   <li>校验 state（Redis TTL=5min，取完即删，防重放攻击）</li>
 *   <li>用 code 换取用户身份信息（userId → 用户详情）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeComOAuthService {

    private static final String WECOM_OAUTH_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String WECOM_GET_TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    private static final String WECOM_GET_USER_INFO_URL = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo";
    private static final String WECOM_GET_USER_DETAIL_URL = "https://qyapi.weixin.qq.com/cgi-bin/user/get";

    private static final String STATE_KEY_PREFIX = "wecom:oauth:state:";
    private static final long STATE_TTL_MINUTES = 5L;

    private static final String ACCESS_TOKEN_KEY_PREFIX = "wecom:access_token:";
    private static final long ACCESS_TOKEN_TTL_SECONDS = 7000L;

    private final WeComProperties weComProperties;
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;

    /**
     * 生成企业微信 OAuth 授权 URL
     * <p>
     * state 用 UUID 生成并存入 Redis（TTL 5分钟），用于回调时防 CSRF 校验。
     *
     * @return 授权页面完整 URL
     */
    public String buildOAuthUrl() {
        String state = UUID.randomUUID().toString().replace("-", "");
        // 存储 state 到 Redis，TTL 5 分钟
        redisTemplate.opsForValue().set(
                STATE_KEY_PREFIX + state,
                "1",
                STATE_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        return UriComponentsBuilder.fromHttpUrl(WECOM_OAUTH_URL)
                .queryParam("appid", weComProperties.getCorpId())
                .queryParam("redirect_uri", weComProperties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "snsapi_base")
                .queryParam("agentid", weComProperties.getAgentId())
                .queryParam("state", state)
                .toUriString() + "#wechat_redirect";
    }

    /**
     * 校验 OAuth 回调中的 state 参数
     * <p>
     * 从 Redis 读取并立即删除（防止重放攻击），不存在则抛出业务异常。
     *
     * @param state 回调中的 state 参数
     * @throws BusinessException state 无效或已过期时抛出
     */
    public void validateState(String state) {
        String key = STATE_KEY_PREFIX + state;
        String value = redisTemplate.opsForValue().getAndDelete(key);
        if (value == null) {
            log.warn("OAuth state 无效或已过期: state={}", state);
            throw new BusinessException(40010, "OAuth state 无效或已过期，请重新发起登录");
        }
    }

    /**
     * 用授权 code 换取企业微信用户信息
     *
     * @param code 企业微信回调的 code（有效期 5 分钟，且只能使用一次）
     * @return 用户信息
     * @throws BusinessException 调用企业微信 API 失败时抛出
     */
    public WeComUserInfo getUserInfo(String code) {
        // Step 1: 获取 access_token
        String accessToken = getAccessToken();

        // Step 2: 用 code + access_token 获取 userId
        String userId = getUserIdByCode(accessToken, code);

        // Step 3: 用 userId 获取用户详情
        return getUserDetail(accessToken, userId);
    }

    /**
     * 获取企业微信 access_token，优先从 Redis 缓存读取（TTL 7000秒）。
     * <p>
     * 企业微信 access_token 有效期 7200 秒，全局唯一；频繁刷新会导致旧 token 立即失效，
     * 多实例部署时会互相刷掉彼此的 token，因此必须缓存复用。
     */
    private String getAccessToken() {
        String cacheKey = ACCESS_TOKEN_KEY_PREFIX + weComProperties.getCorpId();
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String url = UriComponentsBuilder.fromHttpUrl(WECOM_GET_TOKEN_URL)
                .queryParam("corpid", weComProperties.getCorpId())
                .queryParam("corpsecret", weComProperties.getCorpSecret())
                .toUriString();

        try {
            AccessTokenResponse response = restTemplate.getForObject(url, AccessTokenResponse.class);
            if (response == null || response.getErrcode() != 0) {
                String errMsg = response != null ? response.getErrmsg() : "空响应";
                log.error("获取企业微信 access_token 失败: errcode={}, errmsg={}",
                        response != null ? response.getErrcode() : -1, errMsg);
                throw new BusinessException("获取企业微信 access_token 失败: " + errMsg);
            }
            String accessToken = response.getAccessToken();
            // 缓存到 Redis，TTL 保守取 7000 秒（官方 7200 秒，预留 200 秒冗余）
            redisTemplate.opsForValue().set(cacheKey, accessToken, ACCESS_TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
            return accessToken;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用企业微信 gettoken 接口失败", e);
            throw new BusinessException("企业微信服务调用失败: " + e.getMessage());
        }
    }

    /**
     * 用 code 换取企业微信 userId
     */
    private String getUserIdByCode(String accessToken, String code) {
        String url = UriComponentsBuilder.fromHttpUrl(WECOM_GET_USER_INFO_URL)
                .queryParam("access_token", accessToken)
                .queryParam("code", code)
                .toUriString();

        try {
            UserInfoResponse response = restTemplate.getForObject(url, UserInfoResponse.class);
            if (response == null || response.getErrcode() != 0) {
                String errMsg = response != null ? response.getErrmsg() : "空响应";
                log.error("获取企业微信用户信息失败: errcode={}, errmsg={}",
                        response != null ? response.getErrcode() : -1, errMsg);
                throw new BusinessException("获取企业微信用户信息失败: " + errMsg);
            }
            return response.getUserId();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用企业微信 getuserinfo 接口失败", e);
            throw new BusinessException("企业微信服务调用失败: " + e.getMessage());
        }
    }

    /**
     * 获取企业微信用户详细信息
     */
    private WeComUserInfo getUserDetail(String accessToken, String userId) {
        String url = UriComponentsBuilder.fromHttpUrl(WECOM_GET_USER_DETAIL_URL)
                .queryParam("access_token", accessToken)
                .queryParam("userid", userId)
                .toUriString();

        try {
            UserDetailResponse response = restTemplate.getForObject(url, UserDetailResponse.class);
            if (response == null || response.getErrcode() != 0) {
                String errMsg = response != null ? response.getErrmsg() : "空响应";
                log.error("获取企业微信用户详情失败: userId={}, errcode={}, errmsg={}",
                        userId, response != null ? response.getErrcode() : -1, errMsg);
                throw new BusinessException("获取企业微信用户详情失败: " + errMsg);
            }

            WeComUserInfo userInfo = new WeComUserInfo();
            userInfo.setWecomUserId(response.getUserid());
            userInfo.setName(response.getName());
            userInfo.setAvatar(response.getAvatar());
            userInfo.setMobile(response.getMobile());
            return userInfo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用企业微信 user/get 接口失败: userId={}", userId, e);
            throw new BusinessException("企业微信服务调用失败: " + e.getMessage());
        }
    }

    // ======================== 企业微信 API 响应 DTO ========================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AccessTokenResponse {
        private int errcode;
        private String errmsg;
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("expires_in")
        private int expiresIn;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class UserInfoResponse {
        private int errcode;
        private String errmsg;
        @JsonProperty("userid")
        private String userId;
        @JsonProperty("user_ticket")
        private String userTicket;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class UserDetailResponse {
        private int errcode;
        private String errmsg;
        private String userid;
        private String name;
        private String avatar;
        private String mobile;
    }

    // ======================== 用户信息 ========================

    /**
     * 企业微信用户信息
     */
    @Data
    public static class WeComUserInfo {
        /** 企业微信用户 ID */
        private String wecomUserId;
        /** 用户姓名 */
        private String name;
        /** 头像 URL */
        private String avatar;
        /** 手机号 */
        private String mobile;
    }
}
