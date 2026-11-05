package com.laf.lostandfound;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.laf.lostandfound.activities.ItemDetailActivity;
import com.laf.lostandfound.utils.Constants;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class ItemDetailActivityTest {

    @Test
    public void testDeepLinkIntentParsing() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("laf://item/test_item_123"));
        intent.setClass(context, ItemDetailActivity.class);

        assertNotNull(intent.getData());
        assertEquals("laf", intent.getData().getScheme());
        assertEquals("item", intent.getData().getHost());
        assertEquals("test_item_123", intent.getData().getLastPathSegment());
    }

    @Test
    public void testExtraItemIdIntent() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, ItemDetailActivity.class);
        intent.putExtra(Constants.EXTRA_ITEM_ID, "item_999");

        assertEquals("item_999", intent.getStringExtra(Constants.EXTRA_ITEM_ID));
    }
}
