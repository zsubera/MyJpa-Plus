# Contributing

## Development Setup

- JDK 17+
- Maven 3.8+

```bash
git clone https://github.com/zsubera/myjpa-plus
cd myjpa-plus
./mvnw compile
```

## Building

```bash
./mvnw clean verify
```

## Running Tests

```bash
./mvnw test
```

## Code Style

- Use method references (`Entity::getField`) for type safety — never hardcode field name strings
- Add null validation on public API parameters
- Follow existing package structure: `com.zsubera.jpa.spec`
- All condition methods belong in `ConditionBuilder` interface as defaults

## Adding a New Operator

1. Add enum value to `QuerySpec.Op`
2. Add a default method in `ConditionBuilder<E, SELF>`
3. Add a case in `QuerySpec.resolveSimple()`
4. Add tests in `QuerySpecTest`

## Pull Request Checklist

- [ ] Build passes: `./mvnw clean verify`
- [ ] New tests added for new functionality
- [ ] CHANGELOG.md updated
