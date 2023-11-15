package org.btbox.common.license.utils;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.text.StrSplitter;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import de.schlichtherle.license.*;
import lombok.extern.slf4j.Slf4j;
import org.btbox.common.core.exception.ServiceException;
import org.btbox.common.core.utils.DateUtils;
import org.btbox.common.core.utils.SpringUtils;
import org.btbox.common.core.utils.StringUtils;
import org.btbox.common.core.utils.file.FileUtils;
import org.btbox.common.license.properties.LicenseVerifyProperties;

import javax.crypto.SecretKey;
import java.io.*;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * License校验类
 */
@Slf4j
public class LicenseVerify {

    /**
     * 安装License证书
     */
    public synchronized LicenseContent install(LicenseVerifyProperties param){
        LicenseContent result = null;
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        //1. 安装证书
        try{
            LicenseManager licenseManager = LicenseManagerHolder.getInstance(initLicenseParam(param));
            licenseManager.uninstall();
            result = licenseManager.install(new File(param.getLicensePath()));
            log.info(MessageFormat.format("证书安装成功，证书有效期：{0} - {1}",format.format(result.getNotBefore()),format.format(result.getNotAfter())));
        }catch (Exception e){
            log.error("证书安装失败！",e);
        }

        return result;
    }

    /**
     * 校验License证书
     */
    public boolean verify(){
        LicenseManager licenseManager = LicenseManagerHolder.getInstance(null);
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        //2. 校验证书
        try {
            LicenseContent licenseContent = licenseManager.verify();
//             log.debug(MessageFormat.format("证书校验通过，证书有效期：{0} - {1}",format.format(licenseContent.getNotBefore()),format.format(licenseContent.getNotAfter())));
            return true;
        } catch (Exception e){
            log.error("证书校验失败！",e);
            return false;
        }
    }

    /**
     * 初始化证书生成参数
     */
    private LicenseParam initLicenseParam(LicenseVerifyProperties param){
        Preferences preferences = Preferences.userNodeForPackage(LicenseVerify.class);

        CipherParam cipherParam = new DefaultCipherParam(param.getStorePass());

        KeyStoreParam publicStoreParam = new CustomKeyStoreParam(LicenseVerify.class
                ,param.getPublicKeysStorePath()
                ,param.getPublicAlias()
                ,param.getStorePass()
                ,null);

        return new DefaultLicenseParam(param.getSubject()
                ,preferences
                ,publicStoreParam
                ,cipherParam);
    }

    /**
     * 获取License证书信息
     */
    public LicenseContent info(){

        LicenseManager licenseManager = LicenseManagerHolder.getInstance(null);

        //2. 校验证书
        try {
            return licenseManager.verify();
        } catch (Exception e){
            log.error("证书校验失败！",e);
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

        // 判断加密文件是否存在
        boolean licenseTxtExist = FileUtils.exist(licenseTxtPath);
        // 存在文件则解密文件
        if (licenseTxtExist) {

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
            if (!licenseContent.getSubject().equals(subjectName)) {
                throw new ServiceException("错误");
            }

            Date datedTime = DateUtils.dateTime(DateUtils.YYYY_MM_DD_HH_MM_SS, dateTime);
            // 将当前获取的时间 +5分钟
            datedTime.setMinutes(datedTime.getMinutes() + 5);




            File dest = FileUtils.file(licenseTxtPath) ;
            FileOutputStream outputStream = null;
            OutputStreamWriter outputStreamWriter = null;
            try {
                //创建流
                outputStream = new FileOutputStream(dest);
                //新new OutputStreamWriter对象，记得关闭回收
                outputStreamWriter = IoUtil.getUtf8Writer(outputStream);
                String content = subject + ":" ;
                int c;
                for (int i = 0; i < content.length(); i++) {
                    c = content.charAt(i);
                    outputStreamWriter.write((char) c);

                }
                IoUtil.flush(outputStreamWriter);
            } catch (IOException e) {
                //抛出一个运行时异常(直接停止掉程序)
                throw new RuntimeException("运行时异常",e);
            } finally {
                IoUtil.close(outputStream);
                IoUtil.close(outputStreamWriter);
            }



        } else {
            // 如果是安装证书流程则重新覆盖文件
            if (install) {


            } else {
                throw new ServiceException("license.txt文件不存在，请联系供应商");
            }
        }

    }

    public static void main(String[] args) {
//         String content = "BTBOX-BOOT:2023-11-11 11:11:11";
        AES aes = new AES("CBC", "PKCS7Padding",
                // 密钥，可以自定义
                "DYgjCEIMVrj2W9xN".getBytes(),
                // iv加盐，按照实际需求添加
                "DYgjCEIMVrj2W9xN".getBytes());
//
// // 加密为16进制表示
//         String encryptHex = aes.encryptHex(content);
//
//         System.out.println("encryptHex = " + encryptHex);
// // 解密
//         String decryptStr = aes.decryptStr(encryptHex);
//
//         System.out.println("decryptStr = " + decryptStr);

        BufferedInputStream in = FileUtils.getInputStream("G:/my_open_source/btbox-boot/license.txt");
        String content = IoUtil.readUtf8(in);

        String decryptStr = aes.decryptStr(content);

        System.out.println("decryptStr = " + decryptStr);


        String[] split = StringUtils.split(decryptStr, ";");

        // 项目名称
        String subjectName = split[0];
        System.out.println("subjectName = " + subjectName);
        // 时间
        String dateTime = split[1];

        System.out.println("dateTime = " + dateTime);
        

        Date datedTime = DateUtils.dateTime(DateUtils.YYYY_MM_DD_HH_MM_SS, dateTime);
        // 将当前获取的时间 +5分钟
        datedTime.setMinutes(datedTime.getMinutes() + 5);

        System.out.println("datedTime = " + datedTime);

    }

//     public static void main(String[] args) {
//         String content = "BTBOX-BOOT;2023-11-11 11:55:50";
//         AES aes = new AES("CBC", "PKCS7Padding",
//                 // 密钥，可以自定义
//                 "DYgjCEIMVrj2W9xN".getBytes(),
//                 // iv加盐，按照实际需求添加
//                 "DYgjCEIMVrj2W9xN".getBytes());
//
// // 加密为16进制表示
//         String encryptHex = aes.encryptHex(content);
//
//         System.out.println("encryptHex = " + encryptHex);
// // 解密
//         String decryptStr = aes.decryptStr(encryptHex);
//
//         System.out.println("decryptStr = " + decryptStr);
//     }

}
