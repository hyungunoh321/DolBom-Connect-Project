# 돌봄 커넥트 (CareConnect)

> **시흥시 긴급 돌봄 통합 플랫폼** — 긴급 돌봄 예약과 맞춤형 보육 지원금 정보를 한 곳에

<br>

## 📱 프로젝트 소개

**돌봄 커넥트**는 시흥시의 2026년 신규 보육 정책을 기반으로 개발된 Android 네이티브 앱입니다.  
맞벌이 부부를 위해 **초등·영유아 긴급 돌봄 예약**과 **개인 맞춤형 지원금 알림**을 하나의 플랫폼으로 통합합니다.

- **App ID:** `com.siheung.careconnect`
- **Min SDK:** 26 (Android 8.0) / **Target SDK:** 35
- **Language:** Kotlin
- **Team:** Aokey

<br>

## ✨ 주요 기능

| 기능 | 설명 |
|---|---|
| 🔐 **인증** | Supabase Auth 기반 JWT 로그인 / 회원가입 |
| 🎁 **맞춤형 혜택 조회** | 자녀 수·소득분위 조건에 따른 시흥시 보육 지원금 큐레이션 |
| 🗺️ **지도 기반 시설 탐색** | Google Maps SDK로 시흥시 보육기관 위치 확인 및 거리 정렬 |
| 📅 **긴급 돌봄 예약** | 기관 선택 → 날짜·시간 선택 → 예약 확정까지 원스톱 처리 |
| 🔔 **FCM 푸시 알림** | 앱 종료 상태에서도 예약 확정·아이 상태 변경 알림 수신 |
| 👩‍💼 **보육원 관리자** | 예약 현황 조회, 스케줄 등록·수정, 보호자 실시간 알림 전송 |
| 🛡️ **시스템 관리자** | 회원 정보 조회 및 역할(권한) 관리 |

<br>

## 🏗️ 아키텍처

Activity 기반 아키텍처를 채택하며, 각 화면은 독립적인 Activity로 구성됩니다.

```
app/src/main/java/com/siheung/careconnect/
├── login/          # LoginActivity, SignUpActivity, SupabaseClientProvider
├── main/           # MainActivity (홈 — 네비게이션 드로어, 공지 캐러셀, 메뉴 카드)
├── benefits/       # BenefitsActivity, BenefitAdapter, BenefitItem (필터링 혜택 목록)
└── reservation/    # ReservationActivity, FacilityAdapter, ChildcareFacility (Google Maps + 클러스터링)
```

**사용 UI 패턴**
- View Binding (전역 활성화)
- BottomSheetDialogFragment — 시설·혜택 상세 뷰
- RecyclerView + 커스텀 어댑터 — 리스트 화면
- Material Chips — 카테고리 필터

<br>

## 🛠️ 기술 스택

| 분류 | 기술 | 용도 |
|---|---|---|
| **언어** | Kotlin | Android Native 개발 |
| **백엔드** | Supabase (BaaS) | Auth, DB, Storage, Realtime |
| **DB** | Cloud PostgreSQL (Supabase 호스팅) | 예약·정책·시설 데이터 통합 저장 |
| **실시간** | PostgreSQL Realtime (WebSocket) | DB Row 변화 감지 → 즉시 UI 반영 |
| **지도** | Google Maps SDK 18.2.0 + Maps Utils 3.8.2 | 시설 위치 시각화, 클러스터링 |
| **위치** | Google Location Services 21.1.0 | 현재 위치 기반 거리 계산 |
| **알림** | FCM (Firebase Cloud Messaging) | 백그라운드 푸시 알림 |
| **보안** | Supabase RLS | DB 레벨 사용자 데이터 접근 제어 |
| **네트워크** | Ktor Android Client | Supabase SDK 내부 HTTP 처리 |
| **UI** | Material Components 1.10.0 | 컴포넌트 및 테마 |

<br>

## ⚙️ 시작하기

### 사전 요구사항

- Android Studio (최신 안정 버전 권장)
- JDK 17 이상
- 실제 디바이스 또는 Android 에뮬레이터 (API 26+)
- Google Maps API Key
- Supabase 프로젝트 (URL 및 anon key)

### 설치 및 빌드

**1. 저장소 클론**

```bash
git clone https://github.com/your-org/careconnect.git
cd careconnect
```

**2. Google Maps API Key 설정**

프로젝트 루트의 `local.properties` 파일에 아래 내용을 추가합니다.  
이 파일은 `.gitignore`에 포함되어 있으므로 절대 커밋하지 마세요.

```properties
GOOGLE_MAPS_API_KEY=your_google_maps_api_key_here
```

**3. Supabase 설정 확인**

`login/SupabaseClientProvider.kt` 파일에 Supabase URL과 anon key가 설정되어 있는지 확인합니다.

**4. 빌드 및 실행**

```bash
# 디버그 빌드
./gradlew assembleDebug

# 연결된 디바이스에 설치
./gradlew installDebug

# 릴리즈 빌드
./gradlew assembleRelease
```

<br>

## 🧪 테스트 및 개발

```bash
# 유닛 테스트
./gradlew test

# 특정 테스트 실행
./gradlew test --tests "com.siheung.careconnect.ExampleUnitTest"

# 인스트루먼트 테스트 (디바이스 필요)
./gradlew connectedAndroidTest

# 린트 검사
./gradlew lint

# 클린 빌드
./gradlew clean
```

<br>

## 🗄️ 데이터베이스 스키마

Supabase PostgreSQL 기반이며, 5개 테이블로 구성됩니다.

```
users          — 사용자 계정 및 역할 (보호자 / 보육원관리자 / 시스템관리자)
children       — 아이 정보 (parent_id → users)
reservations   — 예약 내역 (parent_id, facility_id, child_id)
facilities     — 보육 시설 정보 (좌표, 정원, 담당 관리자)
policies       — 보육 지원금·정책 (맞춤 추천 조건식 포함)
```

모든 테이블에 **RLS(Row Level Security)** 정책이 적용되어 있으며, 사용자는 자신의 데이터에만 접근할 수 있습니다.

<br>

## 👤 사용자 역할

| 역할 | 접근 범위 |
|---|---|
| **보호자** | 로그인·회원가입, 맞춤형 혜택, 예약 신청·현황, 실시간 아이 정보, 마이페이지 |
| **보육원 관리자** | 예약 현황 조회·상태 변경, 스케줄 등록·수정, 보호자 알림 전송 |
| **시스템 관리자** | 전체 회원 조회, 역할 부여·변경 |

<br>

## 📊 데이터 모델

- **`ChildcareFacility`** — `ClusterItem` 구현체, Google Maps 마커 클러스터링 지원
- **`BenefitItem`** — 혜택 프로그램 (태그, 지원 대상, 신청 링크 포함)
- **`NoticeItem`** — 홈 화면 캐러셀용 공지사항 항목

<br>

## 🔒 보안

- 모든 API 통신 HTTPS 적용
- JWT Access Token 만료: **1시간** / Refresh Token: **7일**
- 비밀번호 bcrypt 해시 저장 (salt rounds ≥ 10)
- Supabase RLS — 사용자는 자신의 Row에만 R/W 가능

<br>

## 👥 팀 소개 (Team Aokey)

| 팀원 | 역할 | 주요 담당 |
|---|---|---|
| **오현근** (팀장) | 프론트엔드 (지도·예약), 프로젝트 관리 | Google Maps SDK 연동, 예약 신청, 예약 현황 확인 |
| **안현빈** | 프론트엔드 (혜택·관리) | 맞춤형 혜택 조회, 시스템 관리자 기능(권한·회원 관리), 마이페이지 |
| **김준서** | 백엔드·인프라·실시간 통신 | Supabase 환경 세팅·DB 스키마, FCM 푸시 알림, 보호자 알림 전송, PostgreSQL Realtime, 비기능 최적화 |
| **김병수** | 프론트엔드 (인증·관리자) | 로그인, 회원가입, 보육원 관리자 스케줄 등록·수정, 관리자 예약 현황 조회 |

<br>

---

> 돌봄 커넥트 · Team Aokey · 2026
