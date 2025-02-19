/*
 * Copyright 2016 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.j4velin.encrypter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Fragment showing the list of isEncrypted files
 */
public class MainActivityFragment extends Fragment implements CryptoCallback {

    private FileAdapter adapter;
    private final static int REQUEST_OUTPUT = 1;
    private File selectedFile;

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        RecyclerView recyclerView =
                (RecyclerView) inflater.inflate(R.layout.fragment_main, container, false);
        Database db = new Database(getContext());
        adapter = new FileAdapter(db.getFiles());
        db.close();
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        return recyclerView;
    }

    @Override
    public void operationComplete(final File resultFile) {
        if (resultFile.isEncrypted) {
            adapter.files.add(resultFile);
            adapter.notifyItemInserted(adapter.files.size());
        } else {
            Snackbar.make(((MainActivity) getActivity()).getCoordinatorLayout(),
                    getString(R.string.file_decrypted, resultFile.name), Snackbar.LENGTH_LONG)
                    .setActionTextColor(getResources().getColor(R.color.colorPrimary, null))
                    .setAction(R.string.open_file, new View.OnClickListener() {
                        @Override
                        public void onClick(final View view) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(resultFile.uri, resultFile.mime);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(intent);
                        }
                    }).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_OUTPUT) {
                try {
                    CryptoUtil.decrypt(getContext(), this, selectedFile, data.getData());
                } catch (GeneralSecurityException e) {
                    Snackbar.make(((MainActivity) getActivity()).getCoordinatorLayout(),
                            getString(R.string.error_security, e.getMessage()),
                            Snackbar.LENGTH_LONG).show();
                } catch (FileNotFoundException e) {
                    Snackbar.make(((MainActivity) getActivity()).getCoordinatorLayout(),
                            R.string.error_file_not_found, Snackbar.LENGTH_LONG).show();
                } catch (IOException e) {
                    Snackbar.make(((MainActivity) getActivity()).getCoordinatorLayout(),
                            getString(R.string.error_io, e.getMessage()), Snackbar.LENGTH_LONG)
                            .show();
                }
            }
        }
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

        private final List<File> files;
        private final View.OnClickListener deleteListener = new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                final int position = (int) view.getTag();
                final File file = files.get(position);
                new AlertDialog.Builder(getContext())
                        .setMessage(getString(R.string.ask_delete, file.name))
                        .setNegativeButton(android.R.string.no,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(final DialogInterface dialogInterface,
                                                        int i) {
                                        dialogInterface.dismiss();
                                    }
                                }).setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialogInterface, int i) {
                                java.io.File f = new java.io.File(file.uri.getPath());
                                if (!f.exists() || f.delete()) {
                                    Database db = new Database(getContext());
                                    db.deleteFile(file.id);
                                    db.close();
                                    files.remove(position);
                                    notifyItemRemoved(position);
                                }
                                dialogInterface.dismiss();
                            }
                        }).create().show();
            }
        };
        private final View.OnClickListener decryptListener = new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                int position = (int) view.getTag();
                selectedFile = files.get(position);
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(selectedFile.mime);
                intent.putExtra(Intent.EXTRA_TITLE, selectedFile.name);
                MainActivityFragment.this.startActivityForResult(intent, REQUEST_OUTPUT);
            }
        };

        private FileAdapter(final List<File> files) {
            this.files = files;
        }

        @Override
        public ViewHolder onCreateViewHolder(final ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.listitem, parent, false);
            v.findViewById(R.id.delete).setOnClickListener(deleteListener);
            v.setOnClickListener(decryptListener);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            File f = files.get(position);
            holder.name.setText(f.name);
            holder.size.setText(File.formatSize(f.size));
            if (f.mime.contains("/")) {
                holder.mime.setContentDescription(f.mime.substring(0, f.mime.indexOf("/")));
            } else {
                holder.mime.setContentDescription(getString(R.string.unknown_file));
            }
            int icon;
            if (f.mime.startsWith("image")) {
                icon = R.drawable.ic_photo;
            } else if (f.mime.startsWith("video")) {
                icon = R.drawable.ic_movie;
            } else if (f.mime.startsWith("audio")) {
                icon = R.drawable.ic_sound;
            } else {
                icon = R.drawable.ic_file;
            }
            holder.mime.setImageResource(icon);
            holder.delete.setTag(position);
            holder.card.setTag(position);
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView mime;
            private final TextView name, size;
            private final View delete;
            private final View card;

            public ViewHolder(final View itemView) {
                super(itemView);
                card = itemView;
                mime = (ImageView) itemView.findViewById(R.id.mime);
                name = (TextView) itemView.findViewById(R.id.name);
                size = (TextView) itemView.findViewById(R.id.size);
                delete = itemView.findViewById(R.id.delete);
            }
        }
    }

}
