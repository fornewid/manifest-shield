# CLAUDE.md

## AGP Manifest Merger ActionType

BlameLogParser는 AGP의 manifest merger blame log를 파싱하여 각 manifest 요소의 source를 식별합니다.

AGP의 `Actions.ActionType` enum (6개):

| ActionType | 의미 | 처리 여부 |
|---|---|---|
| `ADDED` | 요소가 merged manifest에 추가됨 | O |
| `INJECTED` | 빌드 시스템이 주입 (uses-sdk, package 등) | O |
| `MERGED` | 다른 manifest와 병합됨 (중복 권한 등) | O |
| `IMPLIED` | 라이브러리의 targetSdk가 낮아서 암시적으로 추가됨 | O |
| `CONVERTED` | 다른 타입으로 변환됨 (nav-graph → intent-filter) | O |
| `REJECTED` | 충돌로 거부됨 (최종 manifest에 미포함) | X (제외) |

참고: `CONVERTED`는 Navigation의 `<nav-graph>`가 `<intent-filter>`로 변환될 때 사용. AGP의 `NavGraphExpander`에서 발생.

**향후 유지보수**: AGP에 새 ActionType이 추가되면 `BlameLogParser`의 정규식을 업데이트해야 합니다.

## AndroidManifest.xml 요소

AndroidManifest.xml에 새 요소가 추가되면 다음 파일들을 업데이트해야 합니다:

1. `ManifestVisitor.kt` — 새 요소 파싱 추가
2. `ManifestExtraction` — 새 필드 추가
3. `models/` — 새 모델 클래스 생성
4. `ManifestGuardConfiguration.kt` — 새 boolean 플래그 추가
5. `GuardFlags.kt` — 인터페이스에 플래그 추가 + `applyConfig` 확장
6. `ManifestGuardListTask.kt` — 새 카테고리 출력 추가
7. `SourcesContentBuilder.kt` — 새 카테고리를 source별 그룹에 추가
8. `ManifestSourcesDiffTask.kt` — `enabledFlags` map에 추가

참고 문서:
- manifest 요소: https://developer.android.com/guide/topics/manifest/manifest-element
- application 요소: https://developer.android.com/guide/topics/manifest/application-element
