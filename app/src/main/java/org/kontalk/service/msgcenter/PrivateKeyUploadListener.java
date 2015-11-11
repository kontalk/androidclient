package org.kontalk.service.msgcenter;

import android.content.Intent;
import android.text.TextUtils;
import android.util.Base64;

import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.iqregister.packet.Registration;
import org.jivesoftware.smackx.xdata.Form;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.kontalk.client.SmackInitializer;

import java.util.List;

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_UPLOAD_PRIVATEKEY;

/**
 * Uploader and listener for private key upload.
 * @author Alexander Bikadorov
 */
public class PrivateKeyUploadListener extends MessageCenterPacketListener {

    private final byte[] mPrivateKeyData;

    PrivateKeyUploadListener(MessageCenterService instance, byte[] privateKeyData) {
        super(instance);

        mPrivateKeyData = privateKeyData;
    }

    private void configure() {
        SmackInitializer.initializeRegistration();
    }

    private void unconfigure() {
        SmackInitializer.deinitializeRegistration();
    }

    public void uploadAndListen() {
        configure();

        // prepare public key packet
        Stanza iq = prepareKeyPacket();
        if (iq == null)
            return;

        // setup packet filter for response
        StanzaFilter filter = new StanzaIdFilter(iq.getStanzaId());
        getConnection().addAsyncStanzaListener(this, filter);

        // send the key out
        sendPacket(iq);

        // now wait for a response
    }

    @Override
    public void processPacket(Stanza packet) {
        getConnection().removeAsyncStanzaListener(this);

        IQ iq = (IQ) packet;
        if (iq.getType() != IQ.Type.result)
            return;

        DataForm response = iq.getExtension("x", "jabber:x:data");
        if (response == null)
            return;

        String token = null;
        List<FormField> fields = response.getFields();
        for (FormField field : fields) {
            if ("token".equals(field.getVariable())) {
                token = field.getValues().get(0);
                break;
            }
        }

        if (TextUtils.isEmpty(token))
            return;

        Intent i = new Intent(ACTION_UPLOAD_PRIVATEKEY);
        i.putExtra(MessageCenterService.EXTRA_TOKEN, token);
        sendBroadcast(i);

        unconfigure();
    }

    private Stanza prepareKeyPacket() {
        String privatekey = Base64.encodeToString(mPrivateKeyData, Base64.NO_WRAP);

        Registration iq = new Registration();
        iq.setType(IQ.Type.set);
        iq.setTo(getConnection().getServiceName());
        Form form = new Form(DataForm.Type.submit);

        // form type: register#privatekey
        FormField type = new FormField("FORM_TYPE");
        type.setType(FormField.Type.hidden);
        type.addValue("http://kontalk.org/protocol/register#privatekey");
        form.addField(type);

        // private key
        FormField fieldKey = new FormField("privatekey");
        fieldKey.setLabel("Private key");
        fieldKey.setType(FormField.Type.text_single);
        fieldKey.addValue(privatekey);
        form.addField(fieldKey);

        iq.addExtension(form.getDataFormToSend());
        return iq;
    }
}
