# Nội dung cập nhật báo cáo theo source code hiện tại

File này dùng để thay các phần tương ứng trong `baocao.docx`, giữ cách trình bày cũ theo Chương 2, Chương 3 và các bảng actor/use-case. Nội dung dưới đây đã đối chiếu với source Android/Kotlin trong `app/src/main/java/com/example/appbanmypham`.

## Ghi chú đối chiếu nhanh

- Source hiện tại là ứng dụng Android viết bằng Kotlin Jetpack Compose.
- Xác thực dùng Firebase Auth.
- Dữ liệu chính dùng Firebase Firestore; Room dùng làm cache/local database cho `products`, `orders`, `cart_items`, `reviews`.
- Ảnh sản phẩm/gói spa/ảnh tiến trình dùng Cloudinary.
- Không thấy source backend PHP, REST API route, controller hoặc service riêng. Các nghiệp vụ đang nằm trực tiếp trong Activity/model Kotlin và gọi Firebase SDK.
- Không thấy JWT, OTP quên mật khẩu, 2FA, Momo, ZaloPay, thẻ ngân hàng, bài viết, khuyến mãi, in vận đơn, khóa/mở tài khoản, sổ địa chỉ giao hàng riêng.

---

# Các phần cần thay trong báo cáo hiện tại

## Thay mục 2.1. Mô tả hệ thống

Hệ thống ứng dụng di động bán mỹ phẩm và đặt lịch spa được xây dựng nhằm hỗ trợ ba nhóm đối tượng chính: khách hàng (User), quản trị viên (Admin) và tư vấn viên spa. Ứng dụng cho phép khách hàng đăng ký, đăng nhập, duyệt danh sách sản phẩm mỹ phẩm, tìm kiếm sản phẩm, xem chi tiết sản phẩm, thêm sản phẩm vào giỏ hàng, đặt hàng, thanh toán bằng COD hoặc chuyển khoản VietQR, theo dõi lịch sử đơn hàng, hủy đơn khi còn ở trạng thái cho phép, gửi yêu cầu trả hàng và đánh giá sản phẩm đã mua.

Ngoài nhóm chức năng bán mỹ phẩm, source hiện tại đã có nhóm chức năng spa. Khách hàng có thể xem danh sách gói spa, xem chi tiết gói spa, đặt lịch hẹn theo ngày giờ còn sức chứa, theo dõi lịch spa của mình, trao đổi với tư vấn viên qua chat, theo dõi liệu trình spa theo từng buổi và xem ảnh tiến trình điều trị nếu liệu trình yêu cầu.

Quản trị viên sử dụng khu vực quản trị trong ứng dụng để xem dashboard tổng quan, quản lý sản phẩm, thương hiệu, đơn hàng, đánh giá, yêu cầu trả hàng, gói spa, lịch hẹn spa, sức chứa spa, liệu trình spa và phân quyền tài khoản giữa khách hàng, admin, tư vấn viên. Tư vấn viên spa sử dụng khu vực riêng để nhận lịch, quản lý lịch đang phụ trách, cập nhật trạng thái lịch hẹn, trao đổi với khách hàng, ghi chú tư vấn, quản lý buổi điều trị và tải ảnh tiến trình lên hệ thống.

Về kiến trúc triển khai, ứng dụng không có backend PHP hoặc RESTful API riêng trong source. Ứng dụng Android gọi trực tiếp Firebase Auth, Firebase Firestore và Cloudinary. Firestore là cơ sở dữ liệu chính cho dữ liệu đồng bộ thời gian thực; Room được dùng để lưu cục bộ một số dữ liệu sản phẩm, giỏ hàng, đơn hàng và đánh giá.

## Thay mục 2.2.1.1. Chức năng dành cho khách hàng (User)

- Đăng ký tài khoản bằng email và mật khẩu; tài khoản mới được lưu vào Firebase Auth và collection `users` với role mặc định là `CUSTOMER = 0`.
- Đăng nhập, đăng xuất; sau khi đăng nhập hệ thống đọc role trong Firestore để chuyển đến màn hình phù hợp.
- Xem thông tin tài khoản cơ bản từ Firebase Auth. Chưa thấy chức năng cập nhật hồ sơ chi tiết như avatar, ngày sinh, giới tính, loại da.
- Xem danh sách sản phẩm mỹ phẩm, tìm kiếm theo tên, thương hiệu, danh mục; lọc theo danh mục sản phẩm.
- Xem chi tiết sản phẩm gồm ảnh, tên, thương hiệu/danh mục, mô tả, giá, tồn kho; xem sản phẩm liên quan cùng thương hiệu.
- Thêm sản phẩm vào giỏ hàng, tăng/giảm số lượng, xóa sản phẩm, xóa toàn bộ giỏ hàng; dữ liệu giỏ hàng lưu tại `carts/{uid}/items`.
- Đặt hàng từ giỏ hàng, nhập họ tên người nhận, số điện thoại và địa chỉ giao hàng.
- Thanh toán bằng COD hoặc chuyển khoản VietQR MB Bank. Source có `VNPayWebViewActivity`, nhưng chưa thấy màn hình checkout gọi tới VNPay; vì vậy VNPay chỉ nên ghi là có màn hình WebView riêng, chưa thấy tích hợp vào luồng đặt hàng hiện tại.
- Xem lịch sử đơn hàng, lọc theo trạng thái `pending`, `confirmed`, `shipping`, `done`, `cancelled`.
- Xem chi tiết đơn hàng, hủy đơn khi đơn còn `pending`.
- Gửi yêu cầu trả hàng cho đơn đã hoàn thành; theo dõi trạng thái yêu cầu trả hàng.
- Đánh giá sản phẩm trong đơn đã hoàn thành bằng số sao và nhận xét. Chưa thấy đánh giá kèm ảnh/video.
- Xem danh sách gói spa đang hoạt động, tìm kiếm/lọc gói spa theo danh mục.
- Xem chi tiết gói spa và đặt lịch spa.
- Chọn ngày/khung giờ đặt spa theo sức chứa còn trống; hệ thống kiểm tra capacity trước khi tạo lịch.
- Theo dõi lịch spa của mình, hủy lịch spa nếu còn hợp lệ.
- Chat với tư vấn viên trong thread tư vấn.
- Theo dõi liệu trình spa, xem danh sách buổi điều trị, đặt lịch cho buổi chưa lên lịch, xem ảnh tiến trình.

## Thay mục 2.2.1.2. Chức năng dành cho quản trị viên (Admin)

- Đăng nhập bằng tài khoản có role `ADMIN = 1`.
- Xem dashboard tổng quan: số sản phẩm, thương hiệu, đơn hàng, đánh giá, yêu cầu trả hàng, gói spa, lịch hẹn spa, liệu trình và doanh thu đơn hàng/spa.
- Quản lý sản phẩm: thêm, sửa, xóa, ẩn/hiện sản phẩm, cập nhật tên, giá, tồn kho, mô tả, thương hiệu, danh mục, ảnh sản phẩm. Ảnh upload qua Cloudinary.
- Quản lý thương hiệu: thêm, sửa, xóa, tìm kiếm thương hiệu.
- Quản lý đơn hàng: xem danh sách, tìm kiếm, xem chi tiết, cập nhật trạng thái theo luồng `pending -> confirmed -> shipping -> done` hoặc hủy.
- Quản lý đánh giá: xem danh sách đánh giá, lọc/tìm kiếm, ẩn/hiện, xóa đánh giá. Chưa thấy chức năng admin trả lời đánh giá.
- Quản lý yêu cầu trả hàng: xem yêu cầu, xem chi tiết, chấp nhận, từ chối kèm ghi chú, hoàn tất yêu cầu.
- Quản lý gói spa: thêm, sửa, xóa, ẩn/hiện gói spa; cấu hình loại gói 1 buổi hoặc gói liệu trình, giá, thời lượng, số buổi, khoảng cách đề xuất, lợi ích, các bước thực hiện, đối tượng phù hợp, chính sách ảnh tiến trình.
- Quản lý lịch hẹn spa: xem/tìm/lọc lịch hẹn, cập nhật trạng thái, đổi tư vấn viên phụ trách, ghi phòng/giường, chuyên viên dịch vụ và ghi chú nội bộ.
- Quản lý sức chứa spa: cấu hình số khách phục vụ đồng thời, thời lượng slot, khung giờ làm việc, ngày nghỉ trong tuần, số ngày cho phép đặt trước, thời gian grace no-show; cấu hình override cho từng ngày.
- Quản lý liệu trình spa: xem danh sách liệu trình, buổi điều trị và ảnh tiến trình; đổi tư vấn viên phụ trách; ẩn ảnh tiến trình nếu cần.
- Quản lý người dùng ở mức phân quyền: xem danh sách tài khoản trong `users`, lọc theo role và đổi role giữa Khách hàng, Tư vấn viên, Admin. Chưa thấy khóa/mở tài khoản hoặc xem lịch sử mua hàng trong màn quản lý người dùng.

## Bổ sung mục 2.2.1.3. Chức năng dành cho tư vấn viên spa

- Đăng nhập bằng tài khoản có role `CONSULTANT = 2`.
- Xem dashboard tư vấn viên gồm lịch chờ nhận, lịch đang phụ trách và danh sách thread chat.
- Nhận lịch hẹn spa đang chờ.
- Cập nhật trạng thái lịch hẹn: xác nhận, check-in, bắt đầu dịch vụ, hoàn thành, khách không đến, hủy hoặc đổi lịch tùy ngữ cảnh.
- Mở chi tiết lịch hẹn để xem thông tin khách, gói spa, thời gian, ghi chú của khách.
- Ghi chú tình trạng khách hàng, lưu ghi chú tư vấn và đề xuất tư vấn.
- Mở chat riêng với khách hàng, gửi/nhận tin nhắn trong `consultation_chat_messages`.
- Xem hồ sơ khách liên quan đến tư vấn: thông tin user, lịch spa, liệu trình và ảnh tiến trình.
- Quản lý các buổi trong liệu trình: xem buổi, cập nhật trạng thái buổi, hoàn thành/hủy/no-show/đổi lịch.
- Upload ảnh tiến trình trước/sau buổi điều trị theo chính sách của gói spa; ảnh được lưu Cloudinary và metadata lưu trong `treatment_progress_photos`.

---

## Thay mục 2.3. Danh sách actor và use case

### Bảng Danh sách actor

| Actor | Mô tả cập nhật theo source |
|---|---|
| User (Khách hàng) | Người dùng ứng dụng để mua mỹ phẩm và đặt lịch spa. User có thể đăng ký/đăng nhập, xem/tìm/lọc sản phẩm, xem gói spa, quản lý giỏ hàng, đặt hàng, thanh toán COD/VietQR, theo dõi/hủy đơn, gửi yêu cầu trả hàng, đánh giá sản phẩm, đặt lịch spa, chat với tư vấn viên và theo dõi liệu trình spa. |
| Admin (Quản trị viên) | Người có quyền quản trị trong ứng dụng. Admin quản lý sản phẩm, thương hiệu, đơn hàng, đánh giá, trả hàng, gói spa, lịch hẹn spa, cấu hình sức chứa spa, liệu trình spa, dashboard thống kê và phân quyền tài khoản. |
| Tư vấn viên spa | Người phụ trách nghiệp vụ spa. Tư vấn viên nhận lịch hẹn, xác nhận/check-in/bắt đầu/hoàn thành lịch, trao đổi chat với khách hàng, ghi chú tư vấn, theo dõi liệu trình, quản lý buổi điều trị và upload ảnh tiến trình. |

### Bảng Danh sách use case

| Tên Use-Case | Actor | Trạng thái đối chiếu source | Mô tả ngắn cập nhật |
|---|---|---|---|
| Đăng ký tài khoản | User | Có trong source | Tạo tài khoản Firebase Auth bằng email/mật khẩu, lưu document `users` với role khách hàng. |
| Đăng nhập / Đăng xuất | User, Admin, Tư vấn viên | Có trong source | Đăng nhập Firebase Auth, đọc role từ Firestore và chuyển đến màn hình khách/admin/tư vấn viên. |
| Quên mật khẩu | User | Chưa thấy trong source | Báo cáo cũ có OTP/email reset, nhưng source chưa thấy `sendPasswordResetEmail`, OTP hoặc màn quên mật khẩu. |
| Quản lý thông tin cá nhân | User | Một phần | Source có hiển thị thông tin tài khoản cơ bản; chưa thấy cập nhật avatar/ngày sinh/giới tính/loại da. |
| Quản lý địa chỉ giao hàng | User | Chưa thấy trong source | Source chỉ nhập địa chỉ ngay lúc checkout, chưa thấy sổ địa chỉ riêng. |
| Tìm kiếm sản phẩm | User | Có trong source | Tìm theo tên, thương hiệu, danh mục sản phẩm. |
| Lọc sản phẩm theo danh mục | User | Có trong source | Lọc sản phẩm mỹ phẩm theo category. |
| Sắp xếp sản phẩm theo giá/đánh giá/bán chạy | User | Chưa thấy đầy đủ trong source | Source có hiển thị nhóm nổi bật/bán chạy theo tồn kho, chưa thấy control sắp xếp theo giá/rating. |
| Xem chi tiết sản phẩm | User | Có trong source | Xem ảnh, mô tả, giá, tồn kho, thương hiệu/danh mục, sản phẩm cùng thương hiệu. |
| Thêm vào giỏ hàng | User | Có trong source | Lưu sản phẩm vào `carts/{uid}/items`, kiểm tra tồn kho. |
| Quản lý giỏ hàng | User | Có trong source | Xem giỏ, tăng/giảm số lượng, xóa item, xóa toàn bộ giỏ. |
| Đặt hàng | User | Có trong source | Nhập người nhận, số điện thoại, địa chỉ, tạo document `orders`, giảm tồn kho sản phẩm, xóa giỏ hàng. |
| Thanh toán | User | Có một phần | COD và VietQR đã có trong checkout. VNPay có Activity WebView nhưng chưa thấy kết nối vào checkout; Momo/ZaloPay/thẻ ngân hàng chưa thấy. |
| Lịch sử đơn hàng | User | Có trong source | Xem đơn theo user, lọc theo trạng thái. |
| Xem chi tiết đơn hàng | User | Có trong source | Xem sản phẩm, địa chỉ, thanh toán, trạng thái và tổng tiền. |
| Hủy đơn | User | Có trong source | Chỉ cho hủy khi trạng thái đơn là `pending`. |
| Yêu cầu trả hàng | User, Admin | Có trong source | User gửi yêu cầu trả hàng cho đơn `done`; admin duyệt/từ chối/hoàn tất. |
| Đánh giá sản phẩm | User | Có trong source | User đánh giá sản phẩm trong đơn hoàn thành bằng sao và nhận xét. |
| Xem danh sách gói spa | User | Có trong source | Hiển thị gói spa active, tìm kiếm/lọc theo danh mục. |
| Xem chi tiết gói spa | User | Có trong source | Xem mô tả, giá, thời lượng, lợi ích, bước thực hiện, đối tượng phù hợp. |
| Đặt lịch spa | User | Có trong source | Chọn gói spa, ngày, giờ, nhập SĐT/ghi chú; hệ thống kiểm tra sức chứa và tạo lịch. |
| Theo dõi lịch spa của tôi | User | Có trong source | Xem lịch spa của user, mở chat, hủy lịch nếu phù hợp. |
| Chat tư vấn spa | User, Tư vấn viên | Có trong source | Gửi/nhận tin nhắn qua `consultation_chat_threads` và `consultation_chat_messages`. |
| Theo dõi liệu trình spa | User, Tư vấn viên, Admin | Có trong source | Xem kế hoạch điều trị, buổi điều trị, tiến độ, ghi chú và ảnh tiến trình. |
| Đặt lịch buổi điều trị | User | Có trong source | User chọn ngày/giờ cho buổi chưa lên lịch trong liệu trình, có kiểm tra capacity. |
| Upload ảnh tiến trình | Tư vấn viên | Có trong source | Tư vấn viên upload ảnh trước/sau buổi điều trị qua Cloudinary. |
| Quản lý sản phẩm | Admin | Có trong source | Thêm/sửa/xóa/ẩn/hiện sản phẩm, upload ảnh, đồng bộ Room. |
| Quản lý thương hiệu | Admin | Có trong source | Thêm/sửa/xóa/tìm kiếm thương hiệu. |
| Quản lý danh mục | Admin | Một phần | Source có danh mục sản phẩm dạng hằng số/category field, nhưng chưa thấy màn quản lý danh mục riêng. |
| Quản lý đơn hàng | Admin | Có trong source | Xem/tìm/lọc đơn, xem chi tiết, cập nhật trạng thái hợp lệ. |
| Quản lý người dùng | Admin | Một phần | Có danh sách user và đổi role; chưa thấy khóa/mở tài khoản hoặc xem chi tiết lịch sử mua hàng. |
| Quản lý đánh giá | Admin | Có một phần | Có xem, ẩn/hiện, xóa đánh giá; chưa thấy trả lời đánh giá. |
| Quản lý yêu cầu trả hàng | Admin | Có trong source | Chấp nhận, từ chối kèm ghi chú, hoàn tất yêu cầu. |
| Quản lý gói spa | Admin | Có trong source | CRUD, ẩn/hiện, cấu hình gói 1 buổi/liệu trình, ảnh tiến trình. |
| Quản lý lịch hẹn spa | Admin | Có trong source | Xem/tìm/lọc/cập nhật lịch hẹn, đổi tư vấn viên, ghi thông tin vận hành. |
| Quản lý sức chứa spa | Admin | Có trong source | Cấu hình capacity mặc định, giờ làm việc, ngày nghỉ, override từng ngày. |
| Quản lý liệu trình spa | Admin | Có trong source | Xem liệu trình/buổi/ảnh, đổi tư vấn viên, ẩn ảnh tiến trình. |
| Dashboard thống kê | Admin | Có trong source | Tổng hợp sản phẩm, thương hiệu, đơn hàng, doanh thu bán hàng/spa, lịch hẹn, liệu trình, return, review, users. |
| Quản lý bài viết | Admin | Chưa thấy trong source | Báo cáo cũ có bài viết, nhưng source không có model/collection/màn hình bài viết. |
| Quản lý khuyến mãi/voucher | Admin | Chưa thấy trong source | Báo cáo cũ có khuyến mãi, source không có model/collection/màn hình tương ứng. |

---

## Thay mục 2.4. Đặc tả use case

Giữ các use case cũ nếu trạng thái là “Có trong source” hoặc “Một phần”, nhưng cập nhật nội dung theo bảng trên. Các use case dưới đây cần thay/bổ sung rõ nhất:

### 2.4.1. Use case Đăng ký tài khoản

- Tác nhân: User.
- Mục tiêu: Tạo tài khoản khách hàng mới.
- Tiền điều kiện: Người dùng chưa đăng nhập.
- Luồng chính:
  1. User mở màn hình đăng ký.
  2. User nhập email, mật khẩu và xác nhận mật khẩu.
  3. Hệ thống kiểm tra định dạng email, độ dài mật khẩu tối thiểu 6 ký tự và xác nhận mật khẩu khớp.
  4. Hệ thống gọi Firebase Auth để tạo tài khoản.
  5. Hệ thống lưu document `users/{uid}` gồm email và `role = AppRoles.CUSTOMER`.
  6. Hệ thống chuyển về màn hình đăng nhập.
- Ngoại lệ:
  - Email không hợp lệ, mật khẩu dưới 6 ký tự hoặc xác nhận mật khẩu không khớp: hiển thị lỗi.
  - Firebase Auth trả lỗi tạo tài khoản: hiển thị thông báo lỗi.

### 2.4.2. Use case Đăng nhập / Đăng xuất

- Tác nhân: User, Admin, Tư vấn viên.
- Mục tiêu: Xác thực tài khoản và chuyển vào đúng khu vực theo role.
- Tiền điều kiện: Tài khoản đã tồn tại.
- Luồng chính:
  1. Người dùng nhập email và mật khẩu.
  2. Hệ thống gọi Firebase Auth để đăng nhập.
  3. Hệ thống đọc `users/{uid}.role` trong Firestore.
  4. Nếu role là `CUSTOMER`, chuyển đến `ProductActivity`.
  5. Nếu role là `ADMIN`, chuyển đến `DashboardActivity`.
  6. Nếu role là `CONSULTANT`, chuyển đến `ConsultantDashboardActivity`.
  7. Khi đăng xuất, hệ thống gọi Firebase Auth signOut và quay về màn hình đăng nhập.
- Ghi chú: Báo cáo cũ ghi JWT/2FA; chưa thấy trong source.

### 2.4.3. Use case Quên mật khẩu

- Trạng thái: Chưa thấy trong source.
- Ghi chú thay thế: Source hiện tại chưa có màn hình quên mật khẩu, OTP hoặc gọi `sendPasswordResetEmail`. Nếu giữ mục này trong báo cáo cần đánh dấu “chưa thấy trong source”.

### 2.4.4. Use case Quản lý thông tin cá nhân

- Trạng thái: Một phần.
- Source hiện có: hiển thị tên/email cơ bản trong khu vực tài khoản của khách hàng.
- Chưa thấy: cập nhật avatar, ngày sinh, giới tính, loại da, sổ địa chỉ.

### 2.4.5. Use case Tìm kiếm/lọc sản phẩm

- Tác nhân: User.
- Mục tiêu: Tìm sản phẩm mỹ phẩm phù hợp.
- Luồng chính:
  1. User nhập từ khóa tại màn hình sản phẩm.
  2. Hệ thống lọc danh sách sản phẩm không bị ẩn theo tên, thương hiệu hoặc danh mục.
  3. User có thể chọn danh mục để xem sản phẩm trong danh mục đó.
- Ghi chú: Chưa thấy bộ lọc theo rating hoặc sắp xếp theo giá trong source.

### 2.4.6. Use case Đặt hàng và thanh toán

- Tác nhân: User.
- Mục tiêu: Tạo đơn hàng từ giỏ hàng.
- Luồng chính:
  1. User mở giỏ hàng và chọn đặt hàng.
  2. Hệ thống mở checkout với danh sách sản phẩm trong `carts/{uid}/items`.
  3. User nhập họ tên người nhận, số điện thoại và địa chỉ.
  4. User chọn COD hoặc chuyển khoản VietQR.
  5. Hệ thống kiểm tra tồn kho từng sản phẩm bằng Firestore transaction.
  6. Hệ thống tạo document trong `orders`.
  7. Hệ thống giảm tồn kho sản phẩm, lưu order vào Room và xóa giỏ hàng.
- Ngoại lệ:
  - Thiếu thông tin nhận hàng hoặc số điện thoại không đủ 10 số: báo lỗi.
  - Sản phẩm hết hàng/không đủ số lượng: báo lỗi và không tạo đơn.
- Ghi chú: VNPay có file màn hình WebView nhưng chưa thấy được gọi từ checkout; Momo/ZaloPay/thẻ ngân hàng chưa thấy.

### 2.4.7. Use case Yêu cầu trả hàng

- Tác nhân: User, Admin.
- Mục tiêu: User gửi yêu cầu trả hàng cho đơn đã hoàn thành và admin xử lý.
- Luồng chính:
  1. User mở đơn hàng trạng thái `done`.
  2. User chọn “Yêu cầu trả hàng”, nhập lý do và ghi chú.
  3. Hệ thống tạo document trong `return_requests` với trạng thái `pending`.
  4. Admin mở màn hình quản lý trả hàng.
  5. Admin xem chi tiết yêu cầu.
  6. Admin chọn chấp nhận, từ chối kèm ghi chú hoặc hoàn tất.
  7. Hệ thống cập nhật trạng thái và `adminNote`.

### 2.4.8. Use case Đánh giá sản phẩm

- Tác nhân: User.
- Mục tiêu: Đánh giá sản phẩm đã mua.
- Luồng chính:
  1. User mở đơn hàng đã hoàn thành.
  2. Hệ thống hiển thị sản phẩm chưa đánh giá.
  3. User chọn số sao và nhập nhận xét.
  4. Hệ thống lưu đánh giá vào `reviews` và Room.
- Ghi chú: Chưa thấy đánh giá kèm ảnh/video.

### 2.4.9. Use case Đặt lịch spa

- Tác nhân: User.
- Mục tiêu: Đặt lịch hẹn cho gói spa.
- Tiền điều kiện: User đã đăng nhập; gói spa đang active.
- Luồng chính:
  1. User xem danh sách gói spa.
  2. User mở chi tiết gói và chọn đặt lịch.
  3. Hệ thống hiển thị ngày/khung giờ theo cấu hình sức chứa spa.
  4. User nhập số điện thoại và ghi chú.
  5. Hệ thống kiểm tra slot còn chỗ.
  6. Hệ thống tạo document `appointments`.
  7. Nếu gói là liệu trình, hệ thống tạo `treatment_plans`, các `treatment_sessions` và khóa `active_treatment_plan_keys` để tránh đặt trùng liệu trình đang mở.
- Ngoại lệ:
  - Slot đã kín, ngày nghỉ hoặc ngoài giờ làm việc: yêu cầu chọn giờ khác.
  - User chưa đăng nhập: chuyển đến màn hình đăng nhập rồi quay lại booking.

### 2.4.10. Use case Chat tư vấn spa

- Tác nhân: User, Tư vấn viên.
- Mục tiêu: Trao đổi giữa khách hàng và tư vấn viên về lịch hẹn/liệu trình.
- Luồng chính:
  1. Hệ thống tạo hoặc mở `consultation_chat_threads`.
  2. User hoặc tư vấn viên nhập tin nhắn.
  3. Hệ thống lưu tin nhắn vào `consultation_chat_messages`.
  4. Hệ thống cập nhật last message và thời gian mới nhất trên thread.

### 2.4.11. Use case Quản lý lịch hẹn spa

- Tác nhân: Admin, Tư vấn viên.
- Mục tiêu: Theo dõi và vận hành lịch hẹn spa.
- Luồng chính:
  1. Admin hoặc tư vấn viên xem danh sách lịch hẹn trong `appointments`.
  2. Admin có thể đổi tư vấn viên, cập nhật phòng/giường, chuyên viên dịch vụ, ghi chú nội bộ.
  3. Tư vấn viên nhận lịch đang chờ, xác nhận, check-in, bắt đầu dịch vụ, hoàn thành, đánh dấu no-show, hủy hoặc đổi lịch.
  4. Hệ thống cập nhật trạng thái và các mốc thời gian tương ứng trong Firestore.

### 2.4.12. Use case Quản lý liệu trình spa

- Tác nhân: User, Tư vấn viên, Admin.
- Mục tiêu: Theo dõi liệu trình nhiều buổi.
- Luồng chính:
  1. Hệ thống tạo liệu trình khi user đặt gói spa loại `treatment_template`.
  2. User xem tiến độ, ghi chú, đề xuất và danh sách buổi.
  3. User đặt lịch cho buổi chưa có lịch nếu được phép.
  4. Tư vấn viên cập nhật trạng thái buổi và upload ảnh tiến trình.
  5. Admin theo dõi toàn bộ liệu trình, đổi tư vấn viên hoặc ẩn ảnh tiến trình nếu cần.

### 2.4.13. Use case Quản lý sản phẩm

- Cập nhật: Source quản lý sản phẩm gồm tên, giá, tồn kho, mô tả, thương hiệu, danh mục, ảnh, trạng thái ẩn/hiện. Chưa thấy hạn sử dụng, thành phần, hướng dẫn sử dụng, biến thể dung tích/màu như báo cáo cũ.

### 2.4.14. Use case Quản lý người dùng

- Cập nhật: Source hiện có xem danh sách tài khoản và đổi role. Chưa thấy khóa/mở tài khoản, trạng thái tài khoản, xem chi tiết hồ sơ, sổ địa chỉ hoặc lịch sử mua hàng trong màn quản lý người dùng.

### 2.4.15. Use case Quản lý bài viết

- Trạng thái: Chưa thấy trong source.
- Ghi chú: Cần đánh dấu “chưa thấy trong source” hoặc bỏ khỏi danh sách chức năng đã triển khai.

---

## Thay mục 2.6. Biểu đồ lớp / Class - Object

### Bảng Class - Object cập nhật theo model/entity source

| Nhóm | Class/Entity thực tế | Thuộc tính chính | Ghi chú |
|---|---|---|---|
| Phân quyền | `AppRoles` | `CUSTOMER = 0`, `ADMIN = 1`, `CONSULTANT = 2` | Dùng để điều hướng sau đăng nhập. |
| Người dùng | Firestore `users` | `email`, `role` | Không thấy model Kotlin riêng cho User; dữ liệu đọc trực tiếp từ Firestore. |
| Sản phẩm | `Product`, `ProductEntity` | `id`, `name`, `price`, `stock`, `description`, `brandId`, `brandName`, `imageUrl`, `category`, `isHidden`, `createdAt` | `Product` là Room entity; Firestore collection `products`. |
| Danh mục sản phẩm | `ProductCategories` | `VALUES`, `normalize()` | Danh mục định nghĩa trong code, chưa thấy màn quản lý danh mục riêng. |
| Thương hiệu | Firestore `brands`, `BrandData`/`BrandItem` trong UI | `id`, `name`, `description`, `productCount` | Không thấy model global riêng; dùng data class trong Activity. |
| Giỏ hàng | `CartItemEntity` | `productId`, `name`, `price`, `imageUrl`, `brandName`, `quantity`, `updatedAt` | Room entity và Firestore `carts/{uid}/items`. |
| Đơn hàng | `Order`, `OrderItem`, `OrderEntity` | `id`, `userId`, `items`, `totalPrice`, `address`, `phoneNumber`, `receiverName`, `status`, `paymentMethod`, `createdAt` | Firestore `orders`; Room `orders`. |
| Đánh giá | `ReviewEntity` | `id`, `productId`, `orderId`, `userId`, `userName`, `rating`, `comment`, `createdAt`, `isHidden` | Firestore `reviews`; Room `reviews`. |
| Trả hàng | `ReturnRequest` | `id`, `orderId`, `userId`, `reason`, `note`, `status`, `adminNote`, `createdAt`, `updatedAt` | Firestore `return_requests`. |
| Gói spa | `SpaPackage` | `name`, `description`, `category`, `packageType`, `price`, `durationMinutes`, `sessionCount`, `requiresProgressPhotos`, `photoPolicy`, `benefits`, `steps`, `suitableFor`, `isActive` | Firestore `spa_packages`. |
| Lịch hẹn spa | `SpaAppointment` | `userId`, `spaPackageId`, `startAt`, `endAt`, `status`, `consultantId`, `capacityUnits`, `reservedBlockKeys`, `assignedRoomName`, `internalStaffNote` | Firestore `appointments`. |
| Sức chứa spa | `SpaCapacitySettings`, `SpaCapacityOverride`, `AppointmentCapacityBlock` | `defaultConcurrentBookings`, `slotMinutes`, `workingWindows`, `closedWeekdays`, `concurrentBookings`, `bookedCount` | Firestore `spa_capacity_settings`, `spa_capacity_overrides`, `appointment_capacity_blocks`. |
| Liệu trình | `TreatmentPlan` | `appointmentId`, `userId`, `consultantId`, `spaPackageId`, `sessionCount`, `completedSessionCount`, `status`, `consultationNote`, `recommendationNote`, `chatThreadId` | Firestore `treatment_plans`. |
| Buổi điều trị | `TreatmentSession` | `treatmentPlanId`, `appointmentId`, `sessionNumber`, `scheduledStartAt`, `scheduledEndAt`, `status`, `photoPolicy`, `note` | Firestore `treatment_sessions`. |
| Ảnh tiến trình | `TreatmentProgressPhoto` | `treatmentPlanId`, `treatmentSessionId`, `photoType`, `angle`, `imageUrl`, `note`, `uploadedBy`, `isHidden` | Firestore `treatment_progress_photos`; ảnh lưu Cloudinary. |
| Chat tư vấn | `ConsultationChatThread`, `ConsultationChatMessage` | `threadId`, `appointmentId`, `treatmentPlanId`, `userId`, `consultantId`, `senderRole`, `message`, `createdAt` | Firestore `consultation_chat_threads`, `consultation_chat_messages`. |

### Quan hệ giữa các class/collection cập nhật

| Quan hệ | Kiểu quan hệ | Mô tả |
|---|---|---|
| User - Order | 1 - n | Một user có nhiều đơn hàng trong `orders`. |
| Order - OrderItem | 1 - n | Một đơn hàng chứa nhiều sản phẩm trong field `items`. |
| Product - Brand | n - 1 | Một sản phẩm gắn với một thương hiệu qua `brandId`, `brandName`. |
| User - CartItem | 1 - n | Giỏ hàng lưu theo `carts/{uid}/items`. |
| User - Review | 1 - n | User đánh giá sản phẩm đã mua; review có `userId`, `productId`, `orderId`. |
| Order - ReturnRequest | 1 - 0..1/n | User gửi yêu cầu trả hàng liên quan đến order. |
| User - SpaAppointment | 1 - n | Một user có thể đặt nhiều lịch spa. |
| SpaPackage - SpaAppointment | 1 - n | Một gói spa có thể được đặt nhiều lần. |
| Consultant - SpaAppointment | 1 - n | Một tư vấn viên phụ trách nhiều lịch hẹn qua `consultantId`. |
| SpaAppointment - TreatmentPlan | 1 - 0..1 | Lịch đặt gói liệu trình tạo ra một treatment plan. |
| TreatmentPlan - TreatmentSession | 1 - n | Một liệu trình có nhiều buổi điều trị. |
| TreatmentSession - TreatmentProgressPhoto | 1 - n | Một buổi điều trị có thể có nhiều ảnh tiến trình. |
| ConsultationChatThread - ConsultationChatMessage | 1 - n | Một thread chat có nhiều tin nhắn. |
| SpaAppointment - AppointmentCapacityBlock | n - n logic | Lịch hẹn giữ chỗ trong nhiều block capacity qua `reservedBlockKeys`. |

### Các class/chức năng trong báo cáo cũ nhưng chưa thấy trong source

- `BaiViet`, quản lý bài viết.
- `KhuyenMai`, voucher/khuyến mãi.
- `LienHe` theo nghĩa liên hệ/chăm sóc khách hàng riêng.
- Hạn sử dụng sản phẩm, biến thể sản phẩm, thành phần, hướng dẫn sử dụng.
- Khóa/mở tài khoản.
- JWT token, OTP, 2FA.
- REST API route/controller/service PHP.

---

## Bổ sung mục đối chiếu database, model/entity, API/service và UI

### Database thực tế

| Loại lưu trữ | Tên | Dữ liệu |
|---|---|---|
| Firebase Auth | Tài khoản đăng nhập | Email/password, current user, sign in/sign out. |
| Firestore | `users` | Email và role tài khoản. |
| Firestore | `products` | Sản phẩm mỹ phẩm. |
| Firestore | `brands` | Thương hiệu. |
| Firestore | `carts/{uid}/items` | Giỏ hàng từng user. |
| Firestore | `orders` | Đơn hàng. |
| Firestore | `reviews` | Đánh giá sản phẩm. |
| Firestore | `return_requests` | Yêu cầu trả hàng. |
| Firestore | `spa_packages` | Gói spa. |
| Firestore | `appointments` | Lịch hẹn spa. |
| Firestore | `spa_capacity_settings` | Cấu hình sức chứa mặc định. |
| Firestore | `spa_capacity_overrides` | Ghi đè sức chứa/ngày nghỉ theo ngày. |
| Firestore | `appointment_capacity_blocks` | Counter giữ chỗ theo block thời gian. |
| Firestore | `appointment_slots` | Slot đặt lịch cũ/phụ trợ, xuất hiện trong luồng đặt buổi liệu trình. |
| Firestore | `treatment_plans` | Liệu trình spa. |
| Firestore | `treatment_sessions` | Buổi điều trị. |
| Firestore | `treatment_progress_photos` | Ảnh tiến trình. |
| Firestore | `consultation_chat_threads` | Thread chat tư vấn. |
| Firestore | `consultation_chat_messages` | Tin nhắn chat tư vấn. |
| Firestore | `active_treatment_plan_keys` | Khóa chống đặt trùng liệu trình đang mở. |
| Room | `appbanmypham.db` | Local database/cache. |
| Room entity | `products` | Cache sản phẩm. |
| Room entity | `cart_items` | Giỏ hàng local. |
| Room entity | `orders` | Cache đơn hàng user. |
| Room entity | `reviews` | Cache review. |

### API route/controller/service

Không thấy backend route/controller/service trong source. Các thao tác nghiệp vụ hiện nằm ở:

| Nhóm nghiệp vụ | File/màn hình chính |
|---|---|
| Auth/role routing | `MainActivity.kt`, `LoginActivity.kt`, `RegisterActivity.kt` |
| Sản phẩm/giỏ hàng | `ProductActivity.kt`, `ProductDetailsActivity.kt`, `CartActivity.kt` |
| Checkout/order | `CheckoutActivity.kt`, `OrderActivity.kt`, `ManageOrderActivity.kt` |
| Review | `ReviewScreen.kt`, `ManageReviewActivity.kt` |
| Return request | `OrderActivity.kt`, `ManageReturnActivity.kt` |
| Admin dashboard/user role | `DashboardActivity.kt` |
| Spa packages | `SpaPackageDetailsActivity.kt`, `ManageSpaPackageActivity.kt` |
| Spa booking/capacity | `BookSpaAppointmentActivity.kt`, `SpaCapacity.kt`, `ManageSpaCapacityActivity.kt` |
| Spa appointment management | `ManageSpaAppointmentActivity.kt`, `ConsultantDashboardActivity.kt`, `ConsultantAppointmentDetailActivity.kt` |
| Treatment plan/session/photo | `CustomerTreatmentPlanActivity.kt`, `ManageTreatmentActivity.kt`, `ConsultantAppointmentDetailActivity.kt` |
| Consultant chat | `CustomerAppointmentChatActivity.kt`, `ConsultantChatActivity.kt`, `ConsultantCustomerProfileActivity.kt` |
| Image upload/delete | `CloudinaryHelper.kt` |

### Màn hình UI thực tế

| Nhóm | Màn hình |
|---|---|
| Auth | `LoginActivity`, `RegisterActivity` |
| User bán hàng | `ProductActivity`, `ProductDetailsActivity`, `CartActivity`, `CheckoutActivity`, `OrderActivity`, `ReviewScreen` |
| User spa | `SpaPackageDetailsActivity`, `BookSpaAppointmentActivity`, `CustomerAppointmentChatActivity`, `CustomerTreatmentPlanActivity` |
| Admin | `DashboardActivity`, `ManageProductActivity`, `ManageBrandActivity`, `ManageOrderActivity`, `ManageReviewActivity`, `ManageReturnActivity`, `ManageSpaPackageActivity`, `ManageSpaAppointmentActivity`, `ManageSpaCapacityActivity`, `ManageTreatmentActivity` |
| Tư vấn viên | `ConsultantDashboardActivity`, `ConsultantAppointmentDetailActivity`, `ConsultantChatActivity`, `ConsultantCustomerProfileActivity` |
| Thanh toán phụ trợ | `VNPayWebViewActivity` tồn tại nhưng chưa thấy được gọi từ checkout hiện tại |

---

## Thay/Bổ sung mục 3. Giao diện triển khai

### 3.1. Về phía khách hàng

Các giao diện khách hàng thực tế trong source gồm:

- Giao diện đăng nhập.
- Giao diện đăng ký.
- Giao diện cửa hàng/sản phẩm.
- Giao diện tài khoản cơ bản.
- Giao diện xem danh sách sản phẩm theo danh mục.
- Giao diện xem chi tiết sản phẩm.
- Giao diện giỏ hàng.
- Giao diện checkout: nhập thông tin nhận hàng, chọn COD hoặc VietQR, xem QR nếu chọn chuyển khoản.
- Giao diện lịch sử đơn hàng.
- Giao diện chi tiết đơn hàng.
- Giao diện hủy đơn khi đơn còn chờ xác nhận.
- Giao diện gửi yêu cầu trả hàng cho đơn đã hoàn thành.
- Giao diện đánh giá sản phẩm.
- Giao diện danh sách gói spa.
- Giao diện chi tiết gói spa.
- Giao diện đặt lịch spa.
- Giao diện lịch spa của tôi.
- Giao diện chat với tư vấn viên.
- Giao diện liệu trình của tôi.

### 3.2. Về phía quản trị viên

Các giao diện admin thực tế trong source gồm:

- Dashboard thống kê và điều hướng quản trị.
- Quản lý sản phẩm.
- Quản lý thương hiệu.
- Quản lý đơn hàng.
- Quản lý đánh giá.
- Quản lý yêu cầu trả hàng.
- Quản lý gói spa.
- Quản lý lịch hẹn spa.
- Quản lý sức chứa spa.
- Quản lý liệu trình spa.
- Quản lý phân quyền tài khoản trên dashboard: lọc user theo role và đổi role.

### 3.3. Về phía tư vấn viên

Các giao diện tư vấn viên thực tế trong source gồm:

- Dashboard tư vấn viên: lịch chờ nhận, lịch đang phụ trách, tin nhắn.
- Chi tiết lịch hẹn: thông tin khách, gói spa, ghi chú, đề xuất tư vấn, buổi điều trị, ảnh tiến trình.
- Chat tư vấn viên với khách.
- Hồ sơ khách hàng: thông tin khách, lịch spa, liệu trình, ảnh tiến trình.

---

## Danh sách chức năng trong báo cáo cũ nhưng chưa thấy trong source

Các mục dưới đây nếu vẫn giữ trong báo cáo cần ghi rõ “chưa thấy trong source”:

| Chức năng/mô tả trong báo cáo cũ | Trạng thái |
|---|---|
| Backend PHP, RESTful API route/controller/service | Chưa thấy trong source |
| JWT token | Chưa thấy trong source |
| Đăng nhập admin có 2FA | Chưa thấy trong source |
| Quên mật khẩu bằng OTP/email/SMS | Chưa thấy trong source |
| Quản lý hồ sơ cá nhân chi tiết: avatar, ngày sinh, giới tính, loại da | Chưa thấy đầy đủ trong source |
| Sổ địa chỉ giao hàng: thêm/sửa/xóa/đặt mặc định | Chưa thấy trong source |
| Lọc/sắp xếp sản phẩm theo rating, giá, bán chạy bằng control riêng | Chưa thấy đầy đủ trong source |
| Thanh toán Momo, ZaloPay, thẻ ngân hàng | Chưa thấy trong source |
| VNPay trong luồng checkout | Có `VNPayWebViewActivity`, nhưng chưa thấy được gọi từ checkout |
| Đánh giá kèm ảnh/video | Chưa thấy trong source |
| Admin trả lời đánh giá | Chưa thấy trong source |
| Quản lý danh mục bằng màn riêng | Chưa thấy trong source |
| Hạn sử dụng sản phẩm, biến thể dung tích/màu, thành phần, hướng dẫn sử dụng | Chưa thấy trong model/form sản phẩm hiện tại |
| Khóa/mở tài khoản user | Chưa thấy trong source |
| Quản lý bài viết làm đẹp | Chưa thấy trong source |
| Quản lý khuyến mãi/voucher | Chưa thấy trong source |
| In vận đơn/xuất PDF vận đơn | Chưa thấy trong source |
| Gửi notification cho khách khi đổi trạng thái đơn | Chưa thấy trong source |

## Danh sách chức năng có trong source nhưng báo cáo cũ còn thiếu hoặc mô tả chưa đủ

| Chức năng có trong source | Cần bổ sung/cập nhật trong báo cáo |
|---|---|
| Actor Tư vấn viên spa với role `CONSULTANT = 2` | Bổ sung vào actor, use-case, biểu đồ use case, giao diện. |
| Quản lý gói spa | Bổ sung use-case admin và model `SpaPackage`. |
| Đặt lịch spa có kiểm tra sức chứa | Bổ sung use-case khách hàng, database capacity. |
| Cấu hình sức chứa spa | Bổ sung use-case admin, model `SpaCapacitySettings`, `SpaCapacityOverride`, `AppointmentCapacityBlock`. |
| Quản lý lịch hẹn spa | Bổ sung admin/tư vấn viên. |
| Chat tư vấn | Bổ sung User - Tư vấn viên, model chat thread/message. |
| Quản lý liệu trình spa nhiều buổi | Bổ sung User/Admin/Tư vấn viên, model treatment plan/session. |
| Upload và quản lý ảnh tiến trình | Bổ sung tư vấn viên/admin, model `TreatmentProgressPhoto`, Cloudinary. |
| Yêu cầu trả hàng | Bổ sung User/Admin, model `ReturnRequest`. |
| Admin đổi role tài khoản | Cập nhật quản lý người dùng từ “khóa/mở” sang “phân quyền role”. |
| Room local cache | Bổ sung vào phần thiết kế database/kiến trúc. |
| Cloudinary upload/delete image | Bổ sung vào phần công nghệ/dịch vụ tích hợp. |

