# Frontend Stack Technologies

* Status: accepted
* Deciders: Chris
* Date: 2025-11-05

## Context and Problem Statement

We require a frontend stack that enables rapid development while maintaining simplicity. 
The frontend needs to integrate with a Quarkus backend and provide a responsive, interactive user interface for administrative tasks and user management.

## Decision Drivers

* **Simplicity**: Minimal learning curve and straightforward implementation
* **Development Speed**: Enable rapid prototyping and feature development
* **Minimal Dependencies**: Keep the frontend lightweight and maintainable
* **Server-Side Rendering**: Leverage Quarkus Qute templates for initial page loads

## Considered Options

1. **Full-Featured Framework (React/Vue/Angular)**
2. **Lightweight Framework (Alpine.js, Stimulus, HTMX)**
3. **Vanilla JavaScript with Minimal Dependencies**

## Decision Outcome

Chosen option: **Lightweight Framework (Alpine.js) with Vanilla JavaScript**

### Positive Consequences

* **Rapid Development**: Quick iteration and implementation of features
* **Small Bundle Size**: Faster load times and better performance
* **Simplicity**: Easier to understand and maintain the codebase
* **Progressive Enhancement**: Core functionality works without JavaScript
* **Minimal Build Tooling**: Reduced complexity in the build process

### Negative Consequences

* **Type Safety**: No TypeScript or type checking in the frontend
* **Scalability**: May require refactoring for very complex UIs
* **Ecosystem**: Smaller community compared to larger frameworks

## Technology Choices

### 1. Alpine.js
- **Purpose**: Lightweight reactivity framework for interactive components
- **Benefits**:
  - Minimal learning curve
  - Small footprint (~7KB gzipped)
  - Declarative syntax
  - Plays well with server-rendered HTML

### 2. Bootstrap 5
- **Purpose**: CSS framework for responsive design and UI components
- **Benefits**:
  - Comprehensive component library
  - Responsive grid system
  - Good browser compatibility
  - Extensive documentation

### 3. Vanilla JavaScript with Fetch API
- **Purpose**: AJAX functionality and API communication
- **Benefits**:
  - No additional HTTP client libraries needed
  - Native browser support
  - Simple and straightforward implementation
  - No build step required

### 4. Server-Side Rendering with Qute
- **Purpose**: Initial page rendering and progressive enhancement
- **Benefits**:
  - Fast initial page loads
  - Works without JavaScript
  - Seamless integration with Quarkus backend

## Rejected Options

### OpenAPI/TypeScript Code Generation
- **Reason**: Found to be too cumbersome for frontend development
- **Alternative**: Using vanilla JavaScript with clear API contracts
- **Risk Mitigation**: Clear API documentation and manual type checking

### TypeScript
- **Reason**: Added complexity without sufficient benefit for current scale
- **Alternative**: Vanilla JavaScript with JSDoc comments for documentation
- **Risk Mitigation**: Comprehensive testing and code reviews

## Future Considerations

1. **Type Safety**: Consider gradual adoption of TypeScript if the codebase grows
2. **State Management**: Evaluate state management solutions if complexity increases
3. **Build Tools**: Introduce build tools if bundle optimization becomes necessary
4. **Testing**: Expand test coverage as the frontend grows in complexity

## Links

* [Alpine.js Documentation](https://alpinejs.dev/)
* [Bootstrap 5 Documentation](https://getbootstrap.com/docs/5.0/getting-started/introduction/)
* [MDN Fetch API](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API)
