package com.example.ragtest.ingest.service;

import com.example.ragtest.policy.domain.Policy;
import com.example.ragtest.policy.domain.PolicySourceType;
import com.example.ragtest.policy.repository.PolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

@Service
public class SamplePolicyService {

    private final PolicyRepository policyRepository;

    public SamplePolicyService(PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    @Transactional
    public int upsertSamplePolicies() {
        return samples().stream()
                .mapToInt(this::upsert)
                .sum();
    }

    private int upsert(SamplePolicy sample) {
        String hash = hash(sample.toHashSource());
        Policy policy = policyRepository.findBySourceTypeAndExternalId(PolicySourceType.SAMPLE, sample.externalId())
                .orElseGet(() -> Policy.create(PolicySourceType.SAMPLE, sample.externalId(), sample.title()));
        policy.updateFrom(
                sample.title(),
                sample.summary(),
                sample.supportTarget(),
                sample.selectionCriteria(),
                sample.applicationMethod(),
                sample.applicationStartDate(),
                sample.applicationEndDate(),
                sample.regionName(),
                sample.categoryName(),
                sample.officialUrl(),
                hash
        );
        policyRepository.save(policy);
        return 1;
    }

    private List<SamplePolicy> samples() {
        return List.of(
                new SamplePolicy(
                        "sample-youth-monthly-rent",
                        "청년월세 한시 특별지원",
                        "[테스트용 샘플] 저소득 청년의 월세 부담 완화를 위한 주거비 지원 정책입니다. 실제 신청 조건은 공식 안내에서 재확인해야 합니다.",
                        "부모와 별도 거주하는 무주택 청년 중 소득과 재산 기준을 충족하는 사람을 대상으로 합니다.",
                        "청년 본인 가구와 원가구의 소득 및 재산 기준, 주택 요건, 연령 요건 등을 종합적으로 확인합니다.",
                        "복지로 또는 주소지 관할 주민센터에서 신청할 수 있습니다.",
                        LocalDate.of(2024, 2, 26),
                        LocalDate.of(2026, 12, 31),
                        "전국",
                        "주거지원 테스트 데이터",
                        "https://www.bokjiro.go.kr"
                ),
                new SamplePolicy(
                        "sample-kua",
                        "국민취업지원제도",
                        "[테스트용 샘플] 취업을 원하는 구직자에게 취업지원서비스와 소득지원을 제공하는 제도입니다.",
                        "취업을 희망하는 청년, 저소득 구직자, 장기 미취업자 등 고용지원이 필요한 사람을 대상으로 합니다.",
                        "연령, 소득, 재산, 취업 경험 요건에 따라 I유형과 II유형으로 구분되며 세부 기준 확인이 필요합니다.",
                        "고용24 또는 거주지 관할 고용센터를 통해 신청할 수 있습니다.",
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2026, 12, 31),
                        "전국",
                        "취업지원 테스트 데이터",
                        "https://www.work24.go.kr"
                ),
                new SamplePolicy(
                        "sample-gg-interview",
                        "경기도 청년 면접수당",
                        "[테스트용 샘플] 경기도 청년의 구직활동 부담을 줄이기 위해 면접비를 지원하는 정책입니다.",
                        "경기도에 거주하며 취업 면접에 참여한 청년을 대상으로 합니다.",
                        "신청일 기준 경기도 거주 여부, 면접 참여 증빙, 연령 요건 등을 확인합니다.",
                        "경기도 일자리 관련 온라인 신청 시스템에서 면접 증빙자료와 함께 신청합니다.",
                        LocalDate.of(2025, 5, 1),
                        LocalDate.of(2026, 11, 30),
                        "경기도",
                        "취업지원 테스트 데이터",
                        "https://www.gg.go.kr"
                ),
                new SamplePolicy(
                        "sample-youth-leap-account",
                        "청년도약계좌",
                        "[테스트용 샘플] 청년의 중장기 자산 형성을 돕기 위한 금융 지원 상품입니다.",
                        "나이와 개인소득, 가구소득 기준을 충족하는 청년을 대상으로 합니다.",
                        "가입 연령, 개인소득, 가구소득, 금융소득 종합과세 여부 등 요건을 확인합니다.",
                        "취급 은행 앱 또는 영업점을 통해 가입 가능 여부를 확인하고 신청합니다.",
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2026, 12, 31),
                        "전국",
                        "자산형성 테스트 데이터",
                        "https://ylaccount.kinfa.or.kr"
                ),
                new SamplePolicy(
                        "sample-youth-tomorrow-savings",
                        "청년내일저축계좌",
                        "[테스트용 샘플] 일하는 저소득 청년의 자산 형성을 지원하는 복지성 저축 제도입니다.",
                        "근로 또는 사업소득이 있는 청년 중 소득과 재산 기준을 충족하는 사람을 대상으로 합니다.",
                        "연령, 근로소득, 가구소득, 재산 기준과 교육 이수 및 적립 유지 조건을 확인합니다.",
                        "복지로 또는 읍면동 주민센터에서 신청할 수 있습니다.",
                        LocalDate.of(2025, 5, 1),
                        LocalDate.of(2026, 12, 31),
                        "전국",
                        "자산형성 테스트 데이터",
                        "https://www.bokjiro.go.kr"
                )
        );
    }

    private String hash(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 hash algorithm is not available.", exception);
        }
    }

    private record SamplePolicy(
            String externalId,
            String title,
            String summary,
            String supportTarget,
            String selectionCriteria,
            String applicationMethod,
            LocalDate applicationStartDate,
            LocalDate applicationEndDate,
            String regionName,
            String categoryName,
            String officialUrl
    ) {
        String toHashSource() {
            return String.join("|", title, summary, supportTarget, selectionCriteria, applicationMethod,
                    String.valueOf(applicationStartDate), String.valueOf(applicationEndDate),
                    regionName, categoryName, officialUrl);
        }
    }
}
