/*
 * Copyright 2015 Synced Synapse. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xbmc.kore.ui.sections.audio;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import org.xbmc.kore.R;
import org.xbmc.kore.databinding.GridItemAlbumBinding;
import org.xbmc.kore.host.HostInfo;
import org.xbmc.kore.host.HostManager;
import org.xbmc.kore.jsonrpc.type.PlaylistType;
import org.xbmc.kore.provider.MediaContract;
import org.xbmc.kore.ui.AbstractAdditionalInfoFragment;
import org.xbmc.kore.utils.LogUtils;
import org.xbmc.kore.utils.MediaPlayerUtils;
import org.xbmc.kore.utils.UIUtils;

/**
 * Fragment that presents a list of albums of an artist
 */
public class ArtistAlbumsListFragment extends AbstractAdditionalInfoFragment
        implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = LogUtils.makeLogTag(ArtistAlbumsListFragment.class);

    public static final String BUNDLE_KEY_ARTISTID = "artistid";
    public static final String BUNDLE_KEY_ARTISTNAME = "artistname";

    private static final int LOADER = 0;

    private int artistId = -1;

    // Activity listener
    private AlbumListFragment.OnAlbumSelectedListener listenerActivity;

    /**
     * Use this to display all albums for a specific artist
     * @param artistId Artist id
     * @param artistName Artist Name
     */
    public void setAlbum(int artistId, String artistName) {
        Bundle args = new Bundle();
        args.putInt(BUNDLE_KEY_ARTISTID, artistId);
        args.putString(BUNDLE_KEY_ARTISTNAME, artistName);
        setArguments(args);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        if (arguments == null) throw new IllegalStateException("Use setArgs to set required item id");

        artistId = arguments.getInt(BUNDLE_KEY_ARTISTID, -1);
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_artist_albums, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LoaderManager.getInstance(this).initLoader(LOADER, null, this);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listenerActivity = (AlbumListFragment.OnAlbumSelectedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement AlbumListFragment.OnAlbumSelectedListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listenerActivity = null;
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        HostInfo hostInfo = HostManager.getInstance(requireContext()).getHostInfo();
        int hostId = hostInfo != null ? hostInfo.getId() : -1;

        Uri uri = MediaContract.AlbumArtists.buildAlbumsForArtistListUri(hostId, artistId);
        return new CursorLoader(requireContext(),
                                uri,
                                AlbumListFragment.AlbumListQuery.PROJECTION,
                                null, null,
                                AlbumListFragment.AlbumListQuery.SORT_BY_ALBUM);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        if (!data.moveToFirst()) return;
        displayAlbums(data);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) { }

    /**
     * Displays this artists albums
     * @param cursor Cursor with albums
     */
    private void displayAlbums(Cursor cursor) {
        TextView albumsListTitle = requireActivity().findViewById(R.id.albums_title);
        GridLayout albumsList = requireActivity().findViewById(R.id.albums_list);

        if (!cursor.moveToFirst()) { // No albums, hide views
            albumsListTitle.setVisibility(View.GONE);
            albumsList.setVisibility(View.GONE);
            return;
        }
        albumsListTitle.setVisibility(View.VISIBLE);
        albumsList.setVisibility(View.VISIBLE);

        HostManager hostManager = HostManager.getInstance(requireContext());
        View.OnClickListener albumListClickListener = v ->
                listenerActivity.onAlbumSelected((DataHolder) v.getTag(), v.findViewById(R.id.art));

        Resources resources = requireContext().getResources();
        int artWidth = resources.getDimensionPixelOffset(R.dimen.detail_poster_width_square);
        int artHeight = resources.getDimensionPixelOffset(R.dimen.detail_poster_height_square);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        albumsList.removeAllViews();
        do {
            DataHolder dataHolder = new DataHolder(0);
            dataHolder.setId(cursor.getInt(AlbumListFragment.AlbumListQuery.ALBUMID));
            dataHolder.setTitle(cursor.getString(AlbumListFragment.AlbumListQuery.TITLE));
            dataHolder.setUndertitle(cursor.getString(AlbumListFragment.AlbumListQuery.DISPLAYARTIST));
            int year = cursor.getInt(AlbumListFragment.AlbumListQuery.YEAR);
            String genres = cursor.getString(AlbumListFragment.AlbumListQuery.GENRE);
            String desc = (genres != null) ? ((year > 0) ? genres + "  |  " + year : genres) : String.valueOf(year);
            dataHolder.setDescription(desc);
            dataHolder.setPosterUrl(cursor.getString(AlbumListFragment.AlbumListQuery.THUMBNAIL));

            GridItemAlbumBinding binding = GridItemAlbumBinding.inflate(inflater, albumsList, false);
            binding.title.setText(dataHolder.getTitle());
            binding.name.setText(dataHolder.getUnderTitle());
            binding.genres.setText(dataHolder.getDescription());
            UIUtils.loadImageWithCharacterAvatar(requireContext(), hostManager,
                                                 dataHolder.getPosterUrl(),
                                                 dataHolder.getTitle(),
                                                 binding.art, artWidth, artHeight);
            binding.art.setTransitionName("al"+dataHolder.getId());
            binding.listContextMenu.setTag(dataHolder);
            binding.listContextMenu.setOnClickListener(albumContextMenuClickListener);

            View albumView = binding.getRoot();
            albumView.setTag(dataHolder);
            albumView.setOnClickListener(albumListClickListener);
            albumsList.addView(albumView);
        } while (cursor.moveToNext());
    }

    private final View.OnClickListener albumContextMenuClickListener = v -> {
        final DataHolder dataHolder = (DataHolder) v.getTag();

        final PlaylistType.Item playListItem = new PlaylistType.Item();
        playListItem.albumid = dataHolder.getId();

        final PopupMenu popupMenu = new PopupMenu(requireContext(), v);
        popupMenu.getMenuInflater().inflate(R.menu.musiclist_item, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_play) {
                MediaPlayerUtils.play(ArtistAlbumsListFragment.this, playListItem);
                return true;
            } else if (itemId == R.id.action_queue) {
                MediaPlayerUtils.queue(ArtistAlbumsListFragment.this, playListItem, PlaylistType.GetPlaylistsReturnType.AUDIO);
                return true;
            }
            return false;
        });
        popupMenu.show();
    };

    @Override
    public void refresh() {
        LoaderManager.getInstance(this).restartLoader(LOADER, null, this);
    }
}