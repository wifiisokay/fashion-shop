# Stage 1: Build stage
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Sao chép Maven wrapper và pom.xml trước để tận dụng cache Docker layer cho dependencies
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Cấp quyền thực thi cho Maven wrapper và tải dependencies offline
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Sao chép mã nguồn và đóng gói ứng dụng (bỏ qua chạy tests khi build)
COPY src src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Sao chép file jar đã đóng gói từ Stage 1
COPY --from=builder /app/target/*.jar app.jar

# Expose cổng mặc định
EXPOSE 8080

# Chạy ứng dụng Spring Boot
ENTRYPOINT ["java", "-jar", "app.jar"]
