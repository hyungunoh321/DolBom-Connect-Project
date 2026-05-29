# CareConnect

CareConnect는 시흥시 돌봄 서비스를 위한 Android 애플리케이션입니다. 맞춤형 복지 혜택 조회, 보육기관 예약/지도 확인, 로그인 및 회원가입 기능을 중심으로 구성되어 있습니다.

## 주요 기능

- 메인 화면: 주요 서비스 카드와 사이드 드로어 메뉴 제공
- 회원가입/로그인: Supabase Auth 기반 이메일 인증
- 맞춤형 혜택: Supabase `benefits` 테이블에서 혜택 데이터 조회
- 보육기관 예약: Google Maps 기반 보육기관 위치 표시 및 거리순 목록 정렬

## 기술 스택

- Kotlin
- Android ViewBinding
- Material Components
- Google Maps SDK
- Google Play Services Location
- Supabase Kotlin SDK
- Ktor Android Client

## 실행 환경

- Android Studio
- JDK 11 이상
- Android SDK 35
- minSdk 26

## 프로젝트 실행

1. 저장소를 Android Studio에서 엽니다.
2. `local.properties`에 Google Maps API 키를 설정합니다.

```properties
GOOGLE_MAPS_API_KEY=your_google_maps_api_key
```

3. Supabase 프로젝트가 활성화되어 있는지 확인합니다.
4. 앱을 빌드하고 실행합니다.

```bash
./gradlew :app:assembleDebug
```

Windows PowerShell에서는 다음 명령을 사용할 수 있습니다.

```powershell
.\gradlew.bat :app:assembleDebug
```

## Supabase 설정

Supabase 클라이언트 설정은 다음 파일에 있습니다.

```text
app/src/main/java/com/siheung/careconnect/login/SupabaseClient.kt
```

회원가입과 로그인에는 Supabase Auth의 Email provider가 필요합니다. Supabase 대시보드에서 `Authentication > Providers > Email`이 켜져 있어야 합니다.

### 필요한 테이블

회원가입 흐름에서 사용하는 테이블:

- `users`
- `children`

혜택 조회 화면에서 사용하는 테이블:

- `benefits`

`benefits` 테이블은 앱 기준으로 다음 컬럼을 사용합니다.

| 컬럼명 | 타입 예시 | 설명 |
| --- | --- | --- |
| `id` | int8 | 혜택 ID |
| `title` | text | 혜택 이름 |
| `amount` | text | 지원 금액 |
| `category` | text | 필터 카테고리 |
| `tags` | text[] 또는 jsonb | 카드에 표시할 태그 목록 |
| `description` | text | 사업 설명 |
| `target` | text | 지원 대상 |
| `period` | text | 신청/지원 기간 |
| `how_to_apply` | text | 신청 방법 |
| `documents` | text[] 또는 jsonb | 필요 서류 목록 |
| `is_recommended` | bool | 추천 뱃지 표시 여부 |

현재 앱의 필터 카테고리는 다음 값을 기준으로 동작합니다.

```text
긴급돌봄, 양육, 다자녀, 한부모
```

Supabase에서 Row Level Security를 켠 경우, 앱에서 읽기/쓰기 가능한 정책을 별도로 설정해야 합니다. `benefits` 조회가 실패하면 앱에는 "혜택 정보를 불러오지 못했습니다." 메시지가 표시되고, 자세한 오류는 Logcat의 `BenefitsActivity` 태그에서 확인할 수 있습니다.

## 주요 파일

```text
app/src/main/java/com/siheung/careconnect/main/MainActivity.kt
app/src/main/java/com/siheung/careconnect/login/LoginActivity.kt
app/src/main/java/com/siheung/careconnect/login/SignUpActivity.kt
app/src/main/java/com/siheung/careconnect/login/SupabaseClient.kt
app/src/main/java/com/siheung/careconnect/benefits/BenefitsActivity.kt
app/src/main/java/com/siheung/careconnect/reservation/ReservationActivity.kt
```

## 참고 사항

- 보육기관 데이터는 현재 앱 내부 샘플 데이터로 구성되어 있습니다.
- Supabase 프로젝트가 일시 중지되었거나 API 키/URL이 맞지 않으면 회원가입, 로그인, 혜택 조회가 실패할 수 있습니다.
- Google Maps API 키가 비어 있으면 지도 화면이 정상 표시되지 않습니다.
