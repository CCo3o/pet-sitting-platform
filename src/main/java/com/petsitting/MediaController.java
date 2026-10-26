package com.petsitting;

import com.petsitting.auth.AuthService;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api")
public class MediaController {
    private final BookingService bookings;
    private final JdbcTemplate db;
    private final Path root;
    public MediaController(BookingService bookings,JdbcTemplate db,@Value("${app.uploads-dir}") String root){this.bookings=bookings;this.db=db;this.root=Path.of(root).toAbsolutePath().normalize();}
    @PostMapping("/orders/{order}/photos")
    @org.springframework.transaction.annotation.Transactional
    public Map<String,String> upload(@AuthenticationPrincipal Jwt jwt,@PathVariable String order,@RequestParam("file") MultipartFile file) throws IOException {
        bookings.one("SELECT * FROM orders WHERE id=? FOR UPDATE",order);
        long actor=AuthService.actor(jwt); var detail=bookings.detail(actor,order);
        if(((Number)detail.get("SITTER_ID")).longValue()!=actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只有服务喂养员可以上传照片");
        bookings.require("IN_PROGRESS".equals(detail.get("STATUS")),"只能在服务中上传照片");
        bookings.require(db.queryForObject("SELECT COUNT(*) FROM media_files WHERE order_id=?",Integer.class,order)<30,"每单最多上传 30 张照片");
        if(file.isEmpty()||file.getSize()>5*1024*1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请上传 5MB 以内的图片");
        // 检查实际图片格式和尺寸，重新编码为 PNG，不使用客户端文件名或媒体类型。
        BufferedImage decoded;
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仅支持 PNG / JPEG 图片");
            var reader=readers.next();
            try {
                reader.setInput(input); String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!List.of("png","jpeg","jpg").contains(format)||reader.getWidth(0)>4000||reader.getHeight(0)>4000)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"图片格式或尺寸不符合要求，最长边不能超过 4000 像素");
                decoded=reader.read(0);
            } finally {reader.dispose();}
        }
        String id=UUID.randomUUID().toString(),name=id+".png"; Files.createDirectories(root);
        Path path=root.resolve(name);
        ImageIO.write(decoded,"png",path.toFile());
        try {db.update("INSERT INTO media_files VALUES (?,?,?,?,?)",id,actor,order,name,"image/png");}
        catch(RuntimeException ex){Files.deleteIfExists(path);throw ex;}
        return Map.of("id",id);
    }
    @GetMapping("/photos/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> photo(@AuthenticationPrincipal Jwt jwt,@PathVariable String id) throws IOException {
        var file=bookings.one("SELECT * FROM media_files WHERE id=?",id); bookings.detail(AuthService.actor(jwt),(String)file.get("ORDER_ID"));
        Path path=root.resolve((String)file.get("FILE_NAME")).normalize();
        if(!path.startsWith(root)||!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"照片不存在");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(new org.springframework.core.io.FileSystemResource(path));
    }
}
