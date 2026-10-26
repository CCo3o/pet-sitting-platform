package com.petsitting.directory;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DirectoryMapper {
    @Select("SELECT next_id FROM account_counter WHERE id=1 FOR UPDATE") long nextAccountId();
    @Update("UPDATE account_counter SET next_id=next_id+1 WHERE id=1") int advanceAccountId();
    @Select("SELECT * FROM pets WHERE owner_id=#{owner} AND deleted=FALSE ORDER BY name") List<Map<String,Object>> pets(long owner);
    @Select("SELECT * FROM pets WHERE id=#{id}") Map<String,Object> pet(String id);
    @Insert("INSERT INTO pets(id,owner_id,name,notes,species,breed,age_months) VALUES (#{id},#{owner},#{name},#{notes},#{species},#{breed},#{age})")
    int addPet(@Param("id") String id, @Param("owner") long owner, @Param("name") String name, @Param("notes") String notes, @Param("species") String species, @Param("breed") String breed, @Param("age") int age);
    @Update("UPDATE pets SET name=#{name},notes=#{notes},species=#{species},breed=#{breed},age_months=#{age} WHERE id=#{id} AND owner_id=#{owner} AND deleted=FALSE")
    int updatePet(@Param("id") String id, @Param("owner") long owner, @Param("name") String name, @Param("notes") String notes, @Param("species") String species, @Param("breed") String breed, @Param("age") int age);
    @Update("UPDATE pets SET deleted=TRUE WHERE id=#{id} AND owner_id=#{owner}") int deletePet(@Param("id") String id, @Param("owner") long owner);
    @Select("SELECT * FROM addresses WHERE owner_id=#{owner} AND deleted=FALSE ORDER BY contact") List<Map<String,Object>> addresses(long owner);
    @Select("SELECT * FROM addresses WHERE id=#{id}") Map<String,Object> address(String id);
    @Insert("INSERT INTO addresses(id,owner_id,contact,phone,region,detail) VALUES (#{id},#{owner},#{contact},#{phone},#{region},#{detail})")
    int addAddress(@Param("id") String id, @Param("owner") long owner, @Param("contact") String contact, @Param("phone") String phone, @Param("region") String region, @Param("detail") String detail);
    @Update("UPDATE addresses SET contact=#{contact},phone=#{phone},region=#{region},detail=#{detail} WHERE id=#{id} AND owner_id=#{owner} AND deleted=FALSE")
    int updateAddress(@Param("id") String id, @Param("owner") long owner, @Param("contact") String contact, @Param("phone") String phone, @Param("region") String region, @Param("detail") String detail);
    @Update("UPDATE addresses SET deleted=TRUE WHERE id=#{id} AND owner_id=#{owner}") int deleteAddress(@Param("id") String id, @Param("owner") long owner);
    @Select("SELECT p.*,c.display_name FROM sitter_profiles p JOIN credentials c ON c.account_id=p.account_id WHERE p.account_id=#{id}") Map<String,Object> profile(long id);
    @Select("SELECT p.account_id,p.bio,p.region,p.price_cents,c.display_name,COALESCE((SELECT AVG(r.rating) FROM reviews r JOIN orders o ON o.id=r.order_id JOIN slots s ON s.id=o.slot_id WHERE s.sitter_id=p.account_id),0) AS rating FROM sitter_profiles p JOIN credentials c ON c.account_id=p.account_id JOIN accounts a ON a.id=p.account_id WHERE p.status='APPROVED' AND a.enabled=TRUE ORDER BY p.account_id") List<Map<String,Object>> sitters();
    @Select("SELECT p.*,c.display_name FROM sitter_profiles p JOIN credentials c ON c.account_id=p.account_id ORDER BY p.account_id") List<Map<String,Object>> profiles();
    @Insert("INSERT INTO sitter_profiles(account_id,bio,region,price_cents,status) VALUES (#{actor},#{bio},#{region},#{price},'PENDING')")
    int addProfile(@Param("actor") long actor, @Param("bio") String bio, @Param("region") String region, @Param("price") int price);
    @Update("UPDATE sitter_profiles SET bio=#{bio},region=#{region},price_cents=#{price},status='PENDING',review_note='' WHERE account_id=#{actor}")
    int updateProfile(@Param("actor") long actor, @Param("bio") String bio, @Param("region") String region, @Param("price") int price);
    @Update("UPDATE sitter_profiles SET status=#{status},review_note=#{note} WHERE account_id=#{id}") int audit(@Param("id") long id, @Param("status") String status, @Param("note") String note);
}
