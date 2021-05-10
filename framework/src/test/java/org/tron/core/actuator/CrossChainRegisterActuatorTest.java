package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.Constant;
import org.tron.core.Wallet;
import org.tron.core.capsule.*;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Transaction.Result.code;
import org.tron.protos.contract.AssetIssueContractOuterClass;
import org.tron.protos.contract.BalanceContract;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CrossChainRegisterActuatorTest {

  private static final String dbPath = "output_crosschainregister_test";
  private static final String OWNER_ADDRESS;
  private static final String CHAINID;
  private static final String PROXY_ADDRESS;
  private static TronApplicationContext context;
  private static Manager dbManager;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new TronApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS =
        Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
    PROXY_ADDRESS =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
    CHAINID =
        "00000000000000007adbf8dc20423f587a5f3f8ea83e2877e2129c5128c12d1e";
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
  }

  /**
   * Release resources.
   */
  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  /**
   * create temp Capsule test need.
   */
  @Before
  public void initTest() {
    dbManager.getDynamicPropertiesStore().saveBurnedForRegisterCross();
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
  }


  private Any getContract() {
    return Any.pack(
        BalanceContract.CrossChainInfo.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setProxyAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setChainId(Sha256Hash.wrap(ByteArray
                .fromHexString(CHAINID))
                .getByteString())
            .addSrList(ByteString.copyFromUtf8("sr address 1"))
            .setBeginSyncHeight(1L)
            .setMaintenanceTimeInterval(3600L)
            .setParentBlockHash(ByteString.copyFromUtf8("000000000000000029b59068c6058ff466ccf59f2c08a0df1c330b9b7e8dcc4c"))
            .setBlockTime(100000000000L)
            .build());
  }


  /**
   * register cross chain test
   */
  @Test
  public void crossChainRegisterTest() {
    try {
      //1.prepare some data
      byte[] ownerAddress = ByteArray.fromHexString(OWNER_ADDRESS);
      AccountCapsule ownerAccountCapsule =
          new AccountCapsule(
              ByteString.copyFromUtf8(OWNER_ADDRESS),
              ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
              AccountType.Normal,
              300_000_000L);
      ownerAccountCapsule.addAssetAmount("1000001".getBytes(), 1000L);

      Map<String, Long> assetMap = new HashMap<>();
      assetMap.put("1000001",1000L);
      ownerAccountCapsule.addAssetMapV2(assetMap);

      dbManager.getAccountStore().put(ownerAddress, ownerAccountCapsule);
      //2.run test
      CrossChainRegisterActuator actuator = new CrossChainRegisterActuator();
      actuator.setChainBaseManager(dbManager.getChainBaseManager())
          .setAny(getContract());
      TransactionResultCapsule ret = new TransactionResultCapsule();

      actuator.validate();
      actuator.execute(ret);

      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      Assert.assertNotNull(dbManager.getChainBaseManager().getCrossRevokingStore().getChainInfo(CHAINID));
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


}