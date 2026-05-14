# 돌봄 커넥트 API 명세서

| 항목 | 내용 |
| --- | --- |
| 프로젝트명 | 돌봄 커넥트 (CareConnect) |
| 문서 버전 | v1.0 |
| 기준 문서 | `CareConnect_디자인스펙_v1.1.md` |
| 대상 플랫폼 | Android Native (Kotlin) |
| 백엔드 | Supabase Auth, PostgREST, PostgreSQL Realtime, Edge Functions, FCM |

## 1. API 공통 규약

### 1.1 Base URL

```text
SUPABASE_URL=https://{project-ref}.supabase.co
REST_BASE_URL={SUPABASE_URL}/rest/v1
AUTH_BASE_URL={SUPABASE_URL}/auth/v1
FUNCTION_BASE_URL={SUPABASE_URL}/functions/v1
REALTIME_URL=wss://{project-ref}.supabase.co/realtime/v1/websocket
```

### 1.2 공통 헤더

| 헤더 | 값 | 설명 |
| --- | --- | --- |
| `apikey` | `{SUPABASE_ANON_KEY}` | Supabase anon public key |
| `Authorization` | `Bearer {access_token}` | 로그인 후 발급된 JWT |
| `Content-Type` | `application/json` | 요청 본문 형식 |
| `Prefer` | `return=representation` | insert/update 후 변경 row 반환이 필요할 때 사용 |

### 1.3 인증 및 권한

| 역할 | 코드값 | 주요 권한 |
| --- | --- | --- |
| 보호자 | `보호자` | 본인 정보, 자녀 정보, 혜택 조회, 예약 신청/조회/취소, 아이 상태 알림 조회 |
| 보육원 관리자 | `관리자` | 담당 시설 예약 조회/상태 변경, 스케줄 관리, 보호자 알림 발송 |
| 시스템 관리자 | `시스템관리자` | 사용자 목록 조회, 역할 변경, 회원 상세 조회 |

모든 사용자별 데이터 접근은 Supabase RLS 정책으로 제한한다. 클라이언트는 JWT를 전송하고, 서버는 `auth.uid()`와 `users.role`을 기준으로 권한을 판정한다.

### 1.4 공통 응답 및 오류

Supabase PostgREST는 성공 시 배열 또는 객체 JSON을 반환한다. 오류는 다음 형태를 따른다.

```json
{
  "code": "PGRST000",
  "message": "error message",
  "details": "optional details",
  "hint": "optional hint"
}
```

| HTTP 상태 | 의미 |
| --- | --- |
| `200` | 조회/수정 성공 |
| `201` | 생성 성공 |
| `204` | 삭제 또는 반환 없는 수정 성공 |
| `400` | 잘못된 요청값 |
| `401` | 인증 실패 또는 토큰 만료 |
| `403` | 권한 없음 |
| `404` | 대상 리소스 없음 |
| `409` | 중복, 정원 초과, 예약 충돌 |
| `500` | 서버 내부 오류 |

## 2. 데이터 모델

### 2.1 users

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | uuid | Y | Supabase Auth user id |
| `username` | text | Y | 앱 표시용 아이디 또는 이름 |
| `role` | text | Y | `보호자`, `관리자`, `시스템관리자` |
| `created_at` | timestamptz | Y | 생성일 |

### 2.2 children

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | uuid | Y | 자녀 ID |
| `parent_id` | uuid | Y | 보호자 user id |
| `name` | text | Y | 자녀 이름 |
| `birth_date` | date | Y | 생년월일, `YYYY-MM-DD` |
| `gender` | text | N | `남아`, `여아` |
| `income_level` | int | N | 소득분위, 1~10 |

### 2.3 facilities

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | uuid | Y | 시설 ID |
| `name` | text | Y | 시설명 |
| `address` | text | Y | 주소 |
| `latitude` | numeric | Y | 위도 |
| `longitude` | numeric | Y | 경도 |
| `capacity` | int | Y | 기본 수용 인원 |
| `manager_id` | uuid | N | 담당 관리자 user id |
| `created_at` | timestamptz | Y | 등록일 |

### 2.4 facility_schedules

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | uuid | Y | 스케줄 ID |
| `facility_id` | uuid | Y | 시설 ID |
| `date` | date | Y | 운영일 |
| `start_time` | time | Y | 시작 시간 |
| `end_time` | time | Y | 종료 시간 |
| `capacity` | int | Y | 해당 슬롯 정원 |
| `created_at` | timestamptz | Y | 생성일 |
| `updated_at` | timestamptz | Y | 수정일 |

### 2.5 reservations

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | uuid | Y | 예약 ID |
| `parent_id` | uuid | Y | 보호자 user id |
| `facility_id` | uuid | Y | 시설 ID |
| `child_id` | uuid | Y | 자녀 ID |
| `schedule_id` | uuid | N | 스케줄 ID |
| `reserved_at` | timestamptz | Y | 예약 일시 |
| `status` | text | Y | `대기`, `확정`, `완료`, `취소` |
| `created_at` | timestamptz | Y | 생성일 |
| `updated_at` | timestamptz | Y | 수정일 |

### 2.6 policies

현재 Android 앱의 혜택 화면은 `policies` 테이블을 조회한다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | bigint | Y | 정책 ID |
| `title` | text | Y | 정책명 |
| `amount` | text | Y | 지원 금액 표시 문자열 |
| `category` | text | Y | 필터 카테고리 |
| `tags` | text[] | Y | 카드 태그 |
| `description` | text | Y | 설명 |
| `target` | text | Y | 지원 대상 |
| `period` | text | Y | 신청/지원 기간 |
| `how_to_apply` | text | Y | 신청 방법 |
| `documents` | text[] | Y | 필요 서류 |
| `is_recommended` | boolean | Y | 추천 여부 |

### 2.7 child_updates

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | uuid | Y | 업데이트 ID |
| `reservation_id` | uuid | Y | 예약 ID |
| `child_id` | uuid | Y | 자녀 ID |
| `facility_id` | uuid | Y | 시설 ID |
| `manager_id` | uuid | Y | 작성 관리자 |
| `message` | text | Y | 상태 메시지 |
| `image_url` | text | N | 첨부 이미지 URL |
| `is_read` | boolean | Y | 보호자 읽음 여부 |
| `created_at` | timestamptz | Y | 작성일 |

### 2.8 notices

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | uuid | Y | 공지 ID |
| `title` | text | Y | 제목 |
| `body` | text | Y | 본문 |
| `published_at` | timestamptz | Y | 게시일 |
| `target_role` | text | N | 대상 역할, null이면 전체 |

### 2.9 user_devices

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | uuid | Y | 기기 등록 ID |
| `user_id` | uuid | Y | user id |
| `fcm_token` | text | Y | FCM registration token |
| `platform` | text | Y | `android` |
| `created_at` | timestamptz | Y | 생성일 |
| `updated_at` | timestamptz | Y | 갱신일 |

## 3. 인증 API

### 3.1 회원가입

```http
POST /auth/v1/signup
```

요청:

```json
{
  "email": "parent@example.com",
  "password": "password123"
}
```

성공 응답 `200`:

```json
{
  "user": {
    "id": "0b41d7c7-0000-4000-8000-000000000000",
    "email": "parent@example.com"
  },
  "session": null
}
```

후속 처리:

1. `users` 테이블에 `id`, `username`, `role='보호자'`를 생성한다.
2. `children` 테이블에 자녀 정보를 생성한다.

### 3.2 로그인

```http
POST /auth/v1/token?grant_type=password
```

요청:

```json
{
  "email": "parent@example.com",
  "password": "password123"
}
```

성공 응답 `200`:

```json
{
  "access_token": "jwt-access-token",
  "refresh_token": "refresh-token",
  "expires_in": 3600,
  "token_type": "bearer",
  "user": {
    "id": "0b41d7c7-0000-4000-8000-000000000000",
    "email": "parent@example.com"
  }
}
```

### 3.3 토큰 갱신

```http
POST /auth/v1/token?grant_type=refresh_token
```

요청:

```json
{
  "refresh_token": "refresh-token"
}
```

### 3.4 로그아웃

```http
POST /auth/v1/logout
```

헤더:

```text
Authorization: Bearer {access_token}
```

## 4. 사용자 및 자녀 API

### 4.1 내 역할 조회

```http
GET /rest/v1/users?id=eq.{user_id}&select=id,role
```

권한: 로그인 사용자

성공 응답 `200`:

```json
[
  {
    "id": "0b41d7c7-0000-4000-8000-000000000000",
    "role": "보호자"
  }
]
```

### 4.2 내 프로필 조회

```http
GET /rest/v1/users?id=eq.{user_id}&select=id,username,role,created_at,children(*)
```

권한: 본인 또는 시스템 관리자

### 4.3 사용자 추가 정보 생성

```http
POST /rest/v1/users
```

권한: 회원가입 직후 본인

요청:

```json
{
  "id": "0b41d7c7-0000-4000-8000-000000000000",
  "username": "care_parent",
  "role": "보호자"
}
```

### 4.4 자녀 등록

```http
POST /rest/v1/children
```

권한: 보호자

요청:

```json
{
  "parent_id": "0b41d7c7-0000-4000-8000-000000000000",
  "name": "김돌봄",
  "birth_date": "2021-03-15",
  "gender": "여아",
  "income_level": 5
}
```

### 4.5 내 자녀 목록 조회

```http
GET /rest/v1/children?parent_id=eq.{user_id}&select=id,parent_id,name,birth_date,gender,income_level
```

권한: 보호자 본인, 시스템 관리자

## 5. 맞춤형 혜택 API

### 5.1 정책 목록 조회

```http
GET /rest/v1/policies?select=*&order=is_recommended.desc,title.asc
```

권한: 로그인 사용자

쿼리 파라미터:

| 이름 | 예시 | 설명 |
| --- | --- | --- |
| `category` | `eq.양육` | 카테고리 필터 |
| `tags` | `cs.{"다자녀"}` | 태그 포함 필터 |
| `is_recommended` | `eq.true` | 추천 정책만 조회 |

성공 응답 `200`:

```json
[
  {
    "id": 1,
    "title": "긴급돌봄 지원금",
    "amount": "월 20만원",
    "category": "긴급돌봄",
    "tags": ["긴급", "시흥시"],
    "description": "긴급 돌봄이 필요한 가정을 위한 지원",
    "target": "시흥시 거주 보호자",
    "period": "2026.01.01~2026.12.31",
    "how_to_apply": "시흥시 복지 포털 또는 주민센터 신청",
    "documents": ["주민등록등본", "가족관계증명서"],
    "is_recommended": true
  }
]
```

### 5.2 맞춤 추천 정책 조회

```http
POST /rest/v1/rpc/recommend_policies
```

권한: 보호자

요청:

```json
{
  "p_parent_id": "0b41d7c7-0000-4000-8000-000000000000"
}
```

처리 규칙:

- 자녀 수, 자녀 나이, 소득분위, 한부모/다자녀 태그 등 사용자 조건을 기준으로 정책을 필터링한다.
- 반환 형식은 `policies` 목록과 동일하며, `match_reason`을 추가로 포함할 수 있다.

성공 응답 `200`:

```json
[
  {
    "id": 1,
    "title": "긴급돌봄 지원금",
    "amount": "월 20만원",
    "category": "긴급돌봄",
    "tags": ["긴급", "시흥시"],
    "is_recommended": true,
    "match_reason": "자녀 연령과 소득분위 조건에 해당"
  }
]
```

## 6. 시설 및 예약 API

### 6.1 보육 시설 목록 조회

```http
GET /rest/v1/facilities?select=*&order=name.asc
```

권한: 로그인 사용자

쿼리 파라미터:

| 이름 | 예시 | 설명 |
| --- | --- | --- |
| `manager_id` | `eq.{user_id}` | 담당 관리자 시설만 조회 |
| `name` | `ilike.*어린이집*` | 시설명 검색 |

성공 응답 `200`:

```json
[
  {
    "id": "6e1f878c-0000-4000-8000-000000000000",
    "name": "시흥시청 어린이집",
    "address": "시흥시 시청로 20",
    "latitude": 37.3802,
    "longitude": 126.8028,
    "capacity": 20,
    "manager_id": "7d2f878c-0000-4000-8000-000000000000",
    "created_at": "2026-05-01T00:00:00Z"
  }
]
```

### 6.2 시설 상세 조회

```http
GET /rest/v1/facilities?id=eq.{facility_id}&select=*,facility_schedules(*)
```

권한: 로그인 사용자

### 6.3 시설 스케줄 조회

```http
GET /rest/v1/facility_schedules?facility_id=eq.{facility_id}&date=gte.{start_date}&date=lte.{end_date}&select=*
```

권한: 로그인 사용자

성공 응답 `200`:

```json
[
  {
    "id": "9e1f878c-0000-4000-8000-000000000000",
    "facility_id": "6e1f878c-0000-4000-8000-000000000000",
    "date": "2026-05-20",
    "start_time": "09:00:00",
    "end_time": "13:00:00",
    "capacity": 5
  }
]
```

### 6.4 예약 신청

정원 초과와 중복 예약을 방지하기 위해 RPC 사용을 권장한다.

```http
POST /rest/v1/rpc/create_reservation
```

권한: 보호자

요청:

```json
{
  "p_facility_id": "6e1f878c-0000-4000-8000-000000000000",
  "p_child_id": "3c1f878c-0000-4000-8000-000000000000",
  "p_schedule_id": "9e1f878c-0000-4000-8000-000000000000",
  "p_reserved_at": "2026-05-20T09:00:00+09:00"
}
```

성공 응답 `200`:

```json
{
  "id": "1f1f878c-0000-4000-8000-000000000000",
  "parent_id": "0b41d7c7-0000-4000-8000-000000000000",
  "facility_id": "6e1f878c-0000-4000-8000-000000000000",
  "child_id": "3c1f878c-0000-4000-8000-000000000000",
  "schedule_id": "9e1f878c-0000-4000-8000-000000000000",
  "reserved_at": "2026-05-20T00:00:00Z",
  "status": "대기"
}
```

검증 규칙:

| 규칙 | 실패 코드 |
| --- | --- |
| `child_id`는 로그인 보호자의 자녀여야 한다 | `403` |
| 동일 자녀가 같은 시간대에 중복 예약할 수 없다 | `409` |
| 해당 스케줄의 예약 수가 `capacity` 이상이면 생성할 수 없다 | `409` |
| 과거 시간 예약은 불가하다 | `400` |

### 6.5 내 예약 목록 조회

```http
GET /rest/v1/reservations?parent_id=eq.{user_id}&select=*,children(id,name),facilities(id,name,address,latitude,longitude)&order=reserved_at.desc
```

권한: 보호자 본인

### 6.6 예약 취소

```http
PATCH /rest/v1/reservations?id=eq.{reservation_id}
```

권한: 예약한 보호자 본인

요청:

```json
{
  "status": "취소"
}
```

검증 규칙:

- `대기`, `확정` 상태만 취소할 수 있다.
- 예약 시작 시간이 지난 예약은 취소할 수 없다.

### 6.7 관리자 예약 현황 조회

```http
GET /rest/v1/reservations?facility_id=in.({facility_ids})&reserved_at=gte.{start}&reserved_at=lt.{end}&select=*,children(id,name),users!reservations_parent_id_fkey(id,username)&order=reserved_at.asc
```

권한: 보육원 관리자, 시스템 관리자

### 6.8 예약 상태 변경

```http
PATCH /rest/v1/reservations?id=eq.{reservation_id}
```

권한: 해당 시설 관리자, 시스템 관리자

요청:

```json
{
  "status": "확정"
}
```

상태 전이:

```text
대기 -> 확정
대기 -> 취소
확정 -> 완료
확정 -> 취소
```

## 7. 관리자 스케줄 API

### 7.1 스케줄 등록

```http
POST /rest/v1/facility_schedules
```

권한: 해당 시설 관리자, 시스템 관리자

요청:

```json
{
  "facility_id": "6e1f878c-0000-4000-8000-000000000000",
  "date": "2026-05-20",
  "start_time": "09:00:00",
  "end_time": "13:00:00",
  "capacity": 5
}
```

### 7.2 스케줄 수정

```http
PATCH /rest/v1/facility_schedules?id=eq.{schedule_id}
```

권한: 해당 시설 관리자, 시스템 관리자

요청:

```json
{
  "start_time": "10:00:00",
  "end_time": "14:00:00",
  "capacity": 6
}
```

검증 규칙:

- 이미 확정된 예약 수보다 작은 `capacity`로 줄일 수 없다.
- `end_time`은 `start_time`보다 늦어야 한다.

### 7.3 스케줄 삭제

```http
DELETE /rest/v1/facility_schedules?id=eq.{schedule_id}
```

권한: 해당 시설 관리자, 시스템 관리자

검증 규칙:

- 연결된 `대기`, `확정` 예약이 있으면 삭제할 수 없다.

## 8. 실시간 아이 정보 및 알림 API

### 8.1 아이 상태 업데이트 등록

```http
POST /rest/v1/child_updates
```

권한: 해당 시설 관리자, 시스템 관리자

요청:

```json
{
  "reservation_id": "1f1f878c-0000-4000-8000-000000000000",
  "child_id": "3c1f878c-0000-4000-8000-000000000000",
  "facility_id": "6e1f878c-0000-4000-8000-000000000000",
  "message": "점심 식사를 마치고 낮잠 준비 중입니다.",
  "image_url": "https://{project-ref}.supabase.co/storage/v1/object/public/child-updates/sample.jpg"
}
```

성공 후 처리:

- `child_updates` insert 이벤트를 PostgreSQL Realtime으로 보호자 앱에 전달한다.
- Edge Function 또는 DB trigger를 통해 보호자 FCM 토큰으로 push를 발송한다.

### 8.2 내 아이 상태 타임라인 조회

```http
GET /rest/v1/child_updates?child_id=in.({child_ids})&select=*,facilities(id,name)&order=created_at.desc
```

권한: 보호자 본인

### 8.3 읽음 처리

```http
PATCH /rest/v1/child_updates?id=eq.{update_id}
```

권한: 보호자 본인

요청:

```json
{
  "is_read": true
}
```

### 8.4 기기 FCM 토큰 등록

```http
POST /rest/v1/user_devices
```

권한: 로그인 사용자

요청:

```json
{
  "user_id": "0b41d7c7-0000-4000-8000-000000000000",
  "fcm_token": "fcm-registration-token",
  "platform": "android"
}
```

권장 사항:

- `user_id`, `fcm_token` 조합에 unique constraint를 설정한다.
- 토큰 갱신 시 upsert를 사용한다.

### 8.5 FCM 발송 Edge Function

```http
POST /functions/v1/send-child-update-notification
```

권한: 서비스 역할 또는 관리자

요청:

```json
{
  "recipient_user_id": "0b41d7c7-0000-4000-8000-000000000000",
  "title": "아이 상태 업데이트",
  "body": "점심 식사를 마치고 낮잠 준비 중입니다.",
  "data": {
    "type": "child_update",
    "child_update_id": "4a1f878c-0000-4000-8000-000000000000",
    "reservation_id": "1f1f878c-0000-4000-8000-000000000000"
  }
}
```

성공 응답 `200`:

```json
{
  "success": true,
  "sent_count": 1
}
```

## 9. 시스템 관리자 API

### 9.1 회원 목록 조회

```http
GET /rest/v1/users?select=id,username,role,created_at&order=created_at.desc
```

권한: 시스템 관리자

쿼리 파라미터:

| 이름 | 예시 | 설명 |
| --- | --- | --- |
| `role` | `eq.보호자` | 역할 필터 |
| `username` | `ilike.*parent*` | 사용자명 검색 |

### 9.2 회원 상세 조회

```http
GET /rest/v1/users?id=eq.{user_id}&select=id,username,role,created_at,children(parent_id,name,birth_date,income_level)
```

권한: 시스템 관리자

### 9.3 역할 변경

```http
PATCH /rest/v1/users?id=eq.{user_id}
```

권한: 시스템 관리자

요청:

```json
{
  "role": "관리자"
}
```

검증 규칙:

- 허용 역할은 `보호자`, `관리자`, `시스템관리자`만 가능하다.
- 마지막 시스템 관리자는 다른 역할로 변경할 수 없다.

## 10. 공지사항 API

### 10.1 공지 목록 조회

```http
GET /rest/v1/notices?or=(target_role.is.null,target_role.eq.{role})&select=*&order=published_at.desc&limit=3
```

권한: 로그인 사용자

성공 응답 `200`:

```json
[
  {
    "id": "8b1f878c-0000-4000-8000-000000000000",
    "title": "5월 긴급돌봄 지원금 신청 마감 안내",
    "body": "신청 마감일을 확인해주세요.",
    "published_at": "2026-05-01T00:00:00Z",
    "target_role": null
  }
]
```

## 11. Realtime 구독 명세

### 11.1 예약 상태 변경 구독

대상: 보호자 예약 현황, 관리자 예약 현황

```text
schema: public
table: reservations
event: INSERT | UPDATE | DELETE
filter: parent_id=eq.{user_id}
```

관리자는 담당 시설 기준으로 필터링한다.

```text
schema: public
table: reservations
event: INSERT | UPDATE | DELETE
filter: facility_id=in.({facility_ids})
```

### 11.2 아이 상태 업데이트 구독

대상: 보호자 실시간 아이 정보

```text
schema: public
table: child_updates
event: INSERT | UPDATE
filter: child_id=in.({child_ids})
```

### 11.3 스케줄 변경 구독

대상: 예약 신청 화면

```text
schema: public
table: facility_schedules
event: INSERT | UPDATE | DELETE
filter: facility_id=eq.{facility_id}
```

## 12. RLS 정책 요약

| 테이블 | 보호자 | 보육원 관리자 | 시스템 관리자 |
| --- | --- | --- | --- |
| `users` | 본인 row 조회/일부 수정 | 본인 row 조회 | 전체 조회/역할 수정 |
| `children` | 본인 자녀 CRUD | 담당 예약 자녀 조회 | 전체 조회 |
| `policies` | 조회 | 조회 | CRUD |
| `facilities` | 조회 | 담당 시설 조회/수정 | CRUD |
| `facility_schedules` | 조회 | 담당 시설 CRUD | CRUD |
| `reservations` | 본인 예약 CRUD 제한 | 담당 시설 예약 조회/상태 변경 | 전체 CRUD |
| `child_updates` | 본인 자녀 업데이트 조회/읽음 처리 | 담당 시설 업데이트 생성 | 전체 CRUD |
| `notices` | 대상 공지 조회 | 대상 공지 조회 | CRUD |
| `user_devices` | 본인 기기 CRUD | 본인 기기 CRUD | 전체 관리 |

## 13. 구현 우선순위

| 우선순위 | API |
| --- | --- |
| P0 | 회원가입, 로그인, 사용자 역할 조회, 자녀 등록 |
| P0 | 정책 목록 조회 |
| P0 | 시설 목록 조회, 스케줄 조회, 예약 신청, 내 예약 목록 조회 |
| P1 | 예약 취소, 관리자 예약 현황, 예약 상태 변경 |
| P1 | 시스템 관리자 회원 목록, 상세 조회, 역할 변경 |
| P2 | 아이 상태 업데이트, FCM 토큰 등록, FCM 발송 |
| P2 | 공지사항, Realtime 구독 |

## 14. 프론트엔드 연동 메모

- `BenefitsActivity`는 현재 `policies` 테이블을 조회하므로 DB와 시드 데이터도 `policies` 기준으로 준비한다.
- `ReservationActivity`의 샘플 시설 데이터는 `facilities` 조회로 교체한다.
- 로그인 화면의 아이디 입력 필드는 현재 Supabase Auth email로 로그인한다. UI 라벨을 이메일로 맞추거나, 별도 `username -> email` 조회 흐름을 추가해야 한다.
- 시스템 관리자 화면은 이미 `users(id, username, role, created_at)`와 `children(parent_id, name, birth_date, income_level)` 조회 계약과 맞다.
- 예약 생성은 클라이언트에서 직접 `reservations` insert를 수행하지 말고 RPC로 처리해 정원 초과와 중복 예약을 DB 트랜잭션 안에서 막는다.
