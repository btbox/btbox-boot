package org.btbox.common.license.utils;

import cn.hutool.core.io.IoUtil;
import cn.hutool.crypto.symmetric.AES;
import de.schlichtherle.license.*;
import lombok.extern.slf4j.Slf4j;
import org.btbox.common.core.exception.ServiceException;
import org.btbox.common.core.utils.DateUtils;
import org.btbox.common.core.utils.SpringUtils;
import org.btbox.common.core.utils.StringUtils;
import org.btbox.common.core.utils.file.FileUtils;
import org.btbox.common.license.exception.LicenseException;
import org.btbox.common.license.properties.LicenseVerifyProperties;
import org.springframework.stereotype.Component;

import java.io.*;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.prefs.Preferences;

/**
 * License校验类
 */
@Slf4j
public class LicenseVerify {

    public static final DateTimeFormatter LOCALDATETIME_YYYY_MM_DD_HH_MM_SS = DateTimeFormatter.ofPattern(DateUtils.YYYY_MM_DD_HH_MM_SS);

    /**
     * 安装License证书
     */
    public synchronized LicenseContent install(LicenseVerifyProperties param) {
        LicenseContent result = null;
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        // 1. 安装证书
        try {
            LicenseManager licenseManager = LicenseManagerHolder.getInstance(initLicenseParam(param));
            licenseManager.uninstall();
            result = licenseManager.install(new File(param.getLicensePath()));
            log.info(MessageFormat.format("证书安装成功，证书有效期：{0} - {1}", format.format(result.getNotBefore()), format.format(result.getNotAfter())));
        } catch (Exception e) {
            log.error("证书安装失败！", e);
        }

        return result;
    }

    /**
     * 校验License证书
     */
    public boolean verify() {
        LicenseManager licenseManager = LicenseManagerHolder.getInstance(null);
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        // 2. 校验证书
        try {
            LicenseContent licenseContent = licenseManager.verify();
//             log.debug(MessageFormat.format("证书校验通过，证书有效期：{0} - {1}",format.format(licenseContent.getNotBefore()),format.format(licenseContent.getNotAfter())));
            return true;
        } catch (Exception e) {
            log.error("证书校验失败！", e);
            return false;
        }
    }

    /**
     * 初始化证书生成参数
     */
    private LicenseParam initLicenseParam(LicenseVerifyProperties param) {
        Preferences preferences = Preferences.userNodeForPackage(LicenseVerify.class);

        CipherParam cipherParam = new DefaultCipherParam(param.getStorePass());

        KeyStoreParam publicStoreParam = new CustomKeyStoreParam(LicenseVerify.class
                , param.getPublicKeysStorePath()
                , param.getPublicAlias()
                , param.getStorePass()
                , null);

        return new DefaultLicenseParam(param.getSubject()
                , preferences
                , publicStoreParam
                , cipherParam);
    }

    /**
     * 获取License证书信息
     */
    public LicenseContent info() {

        LicenseManager licenseManager = LicenseManagerHolder.getInstance(null);

        // 2. 校验证书
        try {
            return licenseManager.verify();
        } catch (Exception e) {
            log.error("证书校验失败！", e);
            return null;
        }
    }


    public void licenseCryptoFile(boolean install) {

        LicenseVerifyProperties licenseVerifyProperties = SpringUtils.getBean(LicenseVerifyProperties.class);
        String licenseTxtPath = licenseVerifyProperties.getLicenseTxt();

        // 1. 获取证书
        LicenseContent licenseContent = this.info();

        // 获取证书项目名称
        String subject = licenseContent.getSubject();
        // 证书过期时间
        Date notAfter = licenseContent.getNotAfter();

        // 判断加密文件是否存在
        boolean licenseTxtExist = FileUtils.exist(licenseTxtPath);
        // 存在文件则解密文件
        if (licenseTxtExist) {

            existLicenseFileCheck(licenseTxtPath, subject);

        } else {
            // 如果是安装证书流程则创建license.txt文件并且赋予证书结束时间
            if (install) {

                notExistLicenseFileInInstall(licenseTxtPath, notAfter, subject);

            } else {
                throw new LicenseException("license.txt文件不存在,请联系供应商");
            }
        }

    }

    /**
     * 不存在license.txt文件并且是进行安装步骤
     * @param licenseTxtPath
     * @param notAfter
     * @param subject
     */
    private static void notExistLicenseFileInInstall(String licenseTxtPath, Date notAfter, String subject) {
        FileOutputStream outputStream = null;
        OutputStreamWriter outputStreamWriter = null;

        try {

            AES aes = new AES("CBC", "PKCS7Padding",
                    // 密钥，可以自定义
                    "DYgjCEIMVrj2W9xN".getBytes(),
                    // iv加盐，按照实际需求添加
                    "DYgjCEIMVrj2W9xN".getBytes());

            File licenseTxtFile = FileUtils.file(licenseTxtPath);

            // 创建流
            outputStream = new FileOutputStream(licenseTxtFile);
            // 新new OutputStreamWriter对象，记得关闭回收
            outputStreamWriter = IoUtil.getUtf8Writer(outputStream);

            System.out.println(" notAfter.toString() = " + notAfter.toString());

            LocalDateTime licenseAfterTime = LocalDateTime.ofInstant(notAfter.toInstant(), ZoneId.systemDefault());

            String content = subject + ";" + licenseAfterTime.format(LOCALDATETIME_YYYY_MM_DD_HH_MM_SS);
            // 加密重写后的内容
            String encryptContent = aes.encryptHex(content);
            int c;
            for (int i = 0; i < encryptContent.length(); i++) {
                c = encryptContent.charAt(i);
                outputStreamWriter.write((char) c);
            }
            IoUtil.flush(outputStreamWriter);

        } catch (IOException ie) {
            throw new LicenseException("文件写入出错:" + ie.getMessage() + ",证书写入异常,请联系供应商");
        } catch (Exception ex) {
            throw new LicenseException("证书异常,请联系供应商");
        } finally {
            IoUtil.close(outputStream);
            IoUtil.close(outputStreamWriter);
        }
    }

    /**
     * 存在的license.txt文件检查处理
     * @param licenseTxtPath
     * @param subject
     */
    private static void existLicenseFileCheck(String licenseTxtPath, String subject) {
        FileOutputStream outputStream = null;
        OutputStreamWriter outputStreamWriter = null;

        try {
            // 读取加密文件内容
            BufferedInputStream in = FileUtils.getInputStream(licenseTxtPath);
            String inContent = IoUtil.readUtf8(in);

            AES aes = new AES("CBC", "PKCS7Padding",
                    // 密钥，可以自定义
                    "DYgjCEIMVrj2W9xN".getBytes(),
                    // iv加盐，按照实际需求添加
                    "DYgjCEIMVrj2W9xN".getBytes());
            // 解密后的内容
            String decryptStr = aes.decryptStr(inContent);
            String[] split = StringUtils.split(decryptStr, ";");
            // 项目名称
            String subjectName = split[0];
            // 时间
            String dateTime = split[1];

            // 判断项目名称是否一致
            if (!subject.equals(subjectName)) {
                throw new ServiceException("错误");
            }


            LocalDateTime fileDatedTime = LocalDateTime.parse(dateTime, LOCALDATETIME_YYYY_MM_DD_HH_MM_SS);
            // 将当前获取的时间 +5分钟
            LocalDateTime fiveLocalDateTime = fileDatedTime.plusMinutes(5);


            File licenseTxtFile = FileUtils.file(licenseTxtPath);

            // 创建流
            outputStream = new FileOutputStream(licenseTxtFile);
            // 新new OutputStreamWriter对象，记得关闭回收
            outputStreamWriter = IoUtil.getUtf8Writer(outputStream);
            String content = subjectName + ";" + fiveLocalDateTime.format(LOCALDATETIME_YYYY_MM_DD_HH_MM_SS);
            // 加密重写后的内容
            String encryptContent = aes.encryptHex(content);
            int c;
            for (int i = 0; i < encryptContent.length(); i++) {
                c = encryptContent.charAt(i);
                outputStreamWriter.write((char) c);
            }
            IoUtil.flush(outputStreamWriter);

        } catch (IOException ie) {
            throw new LicenseException("文件写入出错:" + ie.getMessage() + ",证书写入异常,请联系供应商");
        } catch (Exception ex) {
            throw new LicenseException("证书异常,请联系供应商");
        } finally {
            IoUtil.close(outputStream);
            IoUtil.close(outputStreamWriter);
        }
    }

//     public static void main(String[] args) {
// //         String content = "BTBOX-BOOT:2023-11-11 11:11:11";
//         AES aes = new AES("CBC", "PKCS7Padding",
//                 // 密钥，可以自定义
//                 "DYgjCEIMVrj2W9xN".getBytes(),
//                 // iv加盐，按照实际需求添加
//                 "DYgjCEIMVrj2W9xN".getBytes());
// //
// // // 加密为16进制表示
// //         String encryptHex = aes.encryptHex(content);
// //
// //         System.out.println("encryptHex = " + encryptHex);
// // // 解密
// //         String decryptStr = aes.decryptStr(encryptHex);
// //
// //         System.out.println("decryptStr = " + decryptStr);
//
//         BufferedInputStream in = FileUtils.getInputStream("D:/license/license.txt");
//         String content = IoUtil.readUtf8(in);
//
//         String decryptStr = aes.decryptStr(content);
//
//         System.out.println("decryptStr = " + decryptStr);
//
//
//         String[] split = StringUtils.split(decryptStr, ";");
//
//         // 项目名称
//         String subjectName = split[0];
//         System.out.println("subjectName = " + subjectName);
//         // 时间
//         String dateTime = split[1];
//
//         System.out.println("dateTime = " + dateTime);
//
//
//         LocalDateTime fileDatedTime = LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern(DateUtils.YYYY_MM_DD_HH_MM_SS));
//         // 将当前获取的时间 +5分钟
//         LocalDateTime localDateTime = fileDatedTime.plusMinutes(5);
//
//         System.out.println("datedTime = " + localDateTime.toString());
//
//     }

    public static void main(String[] args) {
        String content = "BTBOX-BOOT;2023-11-11 11:55:50";
        AES aes = new AES("CBC", "PKCS7Padding",
                // 密钥，可以自定义
                "DYgjCEIMVrj2W9xN".getBytes(),
                // iv加盐，按照实际需求添加
                "DYgjCEIMVrj2W9xN".getBytes());

// 加密为16进制表示
        String encryptHex = aes.encryptHex(content);

        System.out.println("encryptHex = " + encryptHex);
// 解密
        String decryptStr = aes.decryptStr(encryptHex);

        System.out.println("decryptStr = " + decryptStr);

        // 1eccecebe3dbd54e64e81e2e494d9351fd04ed07cd6ac63c1162baddfc9aa819
    }

}
