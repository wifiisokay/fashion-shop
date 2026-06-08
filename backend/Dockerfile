# Stage 1: Build stage
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Sao chép Maven wrapper và pom.xml trước để tận dụng cache Docker layer cho dependencies
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Cấp quyền thực thi cho Maven wrapper và xử lý lỗi line endings (CRLF -> LF) nếu build từ Windows
RUN chmod +x mvnw && sed -i 's/\r$//' mvnw
RUN ./mvnw dependency:go-offline -B

# Sao chép mã nguồn và đóng gói ứng dụng (bỏ qua chạy tests khi build)
COPY src src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Thiết lập múi giờ Việt Nam (UTC+7) cho hệ điều hành trong Container
ENV TZ=Asia/Ho_Chi_Minh

# Sao chép file jar đã đóng gói từ Stage 1
COPY --from=builder /app/target/*.jar app.jar

# Expose cổng mặc định (mang tính tài liệu, Render sẽ tự đè cổng qua biến môi trường PORT)
EXPOSE 8080

# Chạy ứng dụng Spring Boot với cấu hình tối ưu RAM và múi giờ Việt Nam (tránh lệch múi giờ và lỗi OOM Killed)
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Duser.timezone=Asia/Ho_Chi_Minh", "-jar", "app.jar"]
