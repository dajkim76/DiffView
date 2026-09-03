# 프로젝트 코딩 가이드라인

1. **ViewBinding 적극 활용**: 레이아웃 참조 시 `findViewById` 대신 `viewBinding` 기능을 우선적으로 사용합니다.
2. **Import 구문 사용**: 인라인에 전체 패키지 경로(FQCN)를 길게 작성하지 않고, 파일 상단에 `import` 문을 정의하여 간결한 클래스명을 사용합니다.
3. **문자열 리소스화 (strings.xml)**: 소스 코드에 문자열을 직접 하드코딩하지 않고, 가능한 `res/values/strings.xml`에 명확하고 적절한 새로운 키를 정의하여 사용합니다.
