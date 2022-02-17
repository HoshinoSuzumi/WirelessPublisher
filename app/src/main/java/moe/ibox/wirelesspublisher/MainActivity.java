package moe.ibox.wirelesspublisher;

import android.annotation.SuppressLint;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.nle.mylibrary.enums.led.DisplayColor;
import com.nle.mylibrary.enums.led.PlayType;
import com.nle.mylibrary.enums.led.ShowSpeed;
import com.nle.mylibrary.forUse.led.LedScreen;
import com.nle.mylibrary.protocolEntity.DataProtocol;
import com.nle.mylibrary.transfer.CommonListener;
import com.nle.mylibrary.transfer.DataBus;
import com.nle.mylibrary.transfer.DataBusFactory;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private CheckBox checkBox_D4, checkBox_D3, checkBox_D6, checkBox_D5;
    private EditText editText_content;
    private Button button_ctrl_main, button_ctrl_slave, button_ctrl_relay, button_push_led;

    private DataBus zigbeeMainDataBus;

    private final HashMap<String, Boolean> checkedLEDs = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkBox_D4 = findViewById(R.id.checkBox_D4);
        checkBox_D3 = findViewById(R.id.checkBox_D3);
        checkBox_D6 = findViewById(R.id.checkBox_D6);
        checkBox_D5 = findViewById(R.id.checkBox_D5);
        editText_content = findViewById(R.id.editText_content);
        button_ctrl_main = findViewById(R.id.button_ctrl_main);
        button_ctrl_slave = findViewById(R.id.button_ctrl_slave);
        button_ctrl_relay = findViewById(R.id.button_ctrl_relay);
        button_push_led = findViewById(R.id.button_push_led);

        checkBox_D4.setOnCheckedChangeListener(new MyOnCheckedChangeListener());
        checkBox_D3.setOnCheckedChangeListener(new MyOnCheckedChangeListener());
        checkBox_D6.setOnCheckedChangeListener(new MyOnCheckedChangeListener());
        checkBox_D5.setOnCheckedChangeListener(new MyOnCheckedChangeListener());
        button_ctrl_main.setOnClickListener(new MyOnClickListener());
        button_ctrl_slave.setOnClickListener(new MyOnClickListener());
        button_ctrl_relay.setOnClickListener(new MyOnClickListener());
        button_push_led.setOnClickListener(new MyOnClickListener());

        checkedLEDs.put("D4", false);
        checkedLEDs.put("D3", false);
        checkedLEDs.put("D6", false);
        checkedLEDs.put("D5", false);

        zigbeeMainDataBus = DataBusFactory.newSerialDataBus(1, 115200);
        zigbeeMainDataBus.setup();
        zigbeeMainDataBus.setCommonListener(dataProtocol -> {

        });
    }

    enum ZigbeeNodeDefinition {
        MAIN, SLAVE
    }

    class ZigbeeIOCtrlProtocol extends DataProtocol {
        byte nodeId;
        byte ledId;
        boolean on;

        public ZigbeeIOCtrlProtocol(byte nodeId, byte ledId, boolean enable) {
            this.nodeId = nodeId;
            this.ledId = ledId;
            this.on = enable;
        }

        @Override
        public byte[] buildRequestCommand() {
            byte[] command = new byte[]{this.nodeId, this.ledId, (byte) (this.on ? 0x01 : 0x10)};
            Log.i("ZigbeeCtrlCommand", Arrays.toString(command));
            return command;
        }
    }

    private void ZigbeeIOCtrl(ZigbeeNodeDefinition node, byte io, boolean enable) {
        byte nodeId = (byte) (node == ZigbeeNodeDefinition.MAIN ? 0xDD : 0xEE);
        if (zigbeeMainDataBus.openSuccess()) {
            zigbeeMainDataBus.sendProtocol(new ZigbeeIOCtrlProtocol(nodeId, io, enable));
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            Log.e("ZigbeeMain", "Cannot access serial");
        }
    }

    class SendTxtProtocol extends DataProtocol {
        private boolean saveTxt;
        private byte playType1;
        private byte showSpeed;
        private byte stopTime;
        private byte displayColor;
        private byte dataValidTime;
        private byte[] txt;

        public byte[] buildRequestCommand() {
            byte[] preFormat;
            byte[] propertyArea;
            if (this.saveTxt) {
                preFormat = new byte[]{-86, 1, -69, 81, 68};
                propertyArea = new byte[]{this.playType1, this.showSpeed, this.stopTime, this.displayColor};
            } else {
                preFormat = new byte[]{-86, 1, -69, 81, 84};
                propertyArea = new byte[]{this.playType1, this.showSpeed, this.stopTime, this.displayColor, this.dataValidTime};
            }

            byte[] dataArea = new byte[propertyArea.length + this.txt.length];
            System.arraycopy(propertyArea, 0, dataArea, 0, propertyArea.length);
            System.arraycopy(this.txt, 0, dataArea, propertyArea.length, this.txt.length);
            byte[] requestCommand = new byte[dataArea.length + preFormat.length + 1 + 1];
            System.arraycopy(preFormat, 0, requestCommand, 0, preFormat.length);
            requestCommand[preFormat.length] = this.accumulation(dataArea);
            System.arraycopy(dataArea, 0, requestCommand, preFormat.length + 1, dataArea.length);
            requestCommand[requestCommand.length - 1] = -1;
            Log.i("LedCommand2String", Arrays.toString(requestCommand));
            return requestCommand;
        }

        public SendTxtProtocol(boolean saveTxt, PlayType playType, ShowSpeed showSpeed, int stopTime, DisplayColor displayColor, int dataValidTime, String txt) throws UnsupportedEncodingException {
            this.saveTxt = saveTxt;
            this.playType1 = playType.getCode();
            this.showSpeed = showSpeed.getCode();
            this.stopTime = (byte) (stopTime > 127 ? 127 : (Math.max(stopTime, 0)));
            this.displayColor = displayColor.getCode();
            this.dataValidTime = (byte) (dataValidTime > 127 ? 127 : (Math.max(dataValidTime, 0)));
            this.txt = txt.getBytes("gb2312");
        }

        public void receiveMsg(byte[] data, int len) {
        }

        private byte accumulation(byte[] txtByte) {
            byte result = 0;

            for (byte bte : txtByte) {
                result += bte;
            }

            Log.i("accumulation", Arrays.toString(txtByte));
            Log.i("accumulation", String.valueOf(result));

            return result;
        }
    }

    class MyOnClickListener implements View.OnClickListener {
        @SuppressLint("NonConstantResourceId")
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.button_ctrl_main:
                    ZigbeeIOCtrl(ZigbeeNodeDefinition.MAIN, (byte) 0x02, checkBox_D4.isChecked());
                    ZigbeeIOCtrl(ZigbeeNodeDefinition.MAIN, (byte) 0x01, checkBox_D3.isChecked());
                    ZigbeeIOCtrl(ZigbeeNodeDefinition.MAIN, (byte) 0x04, checkBox_D6.isChecked());
                    ZigbeeIOCtrl(ZigbeeNodeDefinition.MAIN, (byte) 0x03, checkBox_D5.isChecked());
                    break;
                case R.id.button_ctrl_slave:
                    ZigbeeIOCtrl(ZigbeeNodeDefinition.SLAVE, (byte) 0x02, checkBox_D4.isChecked());
                    ZigbeeIOCtrl(ZigbeeNodeDefinition.SLAVE, (byte) 0x01, checkBox_D3.isChecked());
                    ZigbeeIOCtrl(ZigbeeNodeDefinition.SLAVE, (byte) 0x04, checkBox_D6.isChecked());
                    ZigbeeIOCtrl(ZigbeeNodeDefinition.SLAVE, (byte) 0x03, checkBox_D5.isChecked());
                    break;
                case R.id.button_ctrl_relay:
                    ZigbeeIOCtrl(ZigbeeNodeDefinition.SLAVE, (byte) 0x05, true);
                    break;
                case R.id.button_push_led:
                    if (zigbeeMainDataBus.openSuccess()) {
                        try {
                            zigbeeMainDataBus.sendProtocol(new SendTxtProtocol(
                                    false,
                                    PlayType.LEFT,
                                    ShowSpeed.SPEED3,
                                    3,
                                    DisplayColor.RED,
                                    100,
                                    editText_content.getText().toString()
                            ));
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Log.e("ZigbeeMainScreen", "Cannot access serial");
                    }
                    break;
            }
        }
    }

    class MyOnCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
        @SuppressLint("NonConstantResourceId")
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            switch (buttonView.getId()) {
                case R.id.checkBox_D4:
                    checkedLEDs.put("D4", isChecked);
                    break;
                case R.id.checkBox_D3:
                    checkedLEDs.put("D3", isChecked);
                    break;
                case R.id.checkBox_D6:
                    checkedLEDs.put("D6", isChecked);
                    break;
                case R.id.checkBox_D5:
                    checkedLEDs.put("D5", isChecked);
                    break;
            }
        }
    }
}