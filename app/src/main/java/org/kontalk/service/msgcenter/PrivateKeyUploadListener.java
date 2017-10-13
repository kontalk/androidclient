/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.service.msgcenter;

import android.content.Intent;
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
 * Uploader and response listener for private key transfer.
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

        // setup packet filter for response
        StanzaFilter filter = new StanzaIdFilter(iq.getStanzaId());
        getConnection().addAsyncStanzaListener(this, filter);

        // send the key out
        sendPacket(iq);

        // now wait for a response
    }

    @Override
    public void processStanza(Stanza packet) {
        getConnection().removeAsyncStanzaListener(this);

        IQ iq = (IQ) packet;
        if (iq.getType() != IQ.Type.result) {
            finish(null);
            return;
        }

        DataForm response = iq.getExtension("x", "jabber:x:data");
        if (response == null) {
            finish(null);
            return;
        }

        String token = null;
        List<FormField> fields = response.getFields();
        for (FormField field : fields) {
            if ("token".equals(field.getVariable())) {
                token = field.getValues().get(0);
                break;
            }
        }

        finish(token);
    }

    private void finish(String token) {
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
