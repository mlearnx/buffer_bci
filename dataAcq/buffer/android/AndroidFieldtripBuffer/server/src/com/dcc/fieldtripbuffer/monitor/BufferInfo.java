package com.dcc.fieldtripbuffer.monitor;

import android.os.Parcel;
import android.os.Parcelable;

import com.dcc.fieldtripbuffer.C;

public class BufferInfo implements Parcelable {
	public final String adress;
	public int nSamples = 0;
	public int nEvents = 0;
	public int dataType = -1;
	public int nChannels = -1;
	public float fSample = -1;
	public final long startTime;
	public boolean changed = true;

	public static final Parcelable.Creator<BufferInfo> CREATOR = new Parcelable.Creator<BufferInfo>() {
		@Override
		public BufferInfo createFromParcel(final Parcel in) {
			return new BufferInfo(in);
		}

		@Override
		public BufferInfo[] newArray(final int size) {
			return new BufferInfo[size];
		}
	};

	private BufferInfo(final Parcel in) {
		final int[] integers = new int[4];

		adress = in.readString();
		in.readIntArray(integers);
		fSample = in.readFloat();
		startTime = in.readLong();

		nSamples = integers[0];
		nEvents = integers[1];
		dataType = integers[2];
		nChannels = integers[3];
	}

	public BufferInfo(final String adress, final long startTime) {
		this.adress = adress;
		this.startTime = startTime;
	}

	@Override
	public int describeContents() {
		return C.BUFFER_INFO_PARCEL;
	}

	@Override
	public void writeToParcel(final Parcel out, final int flags) {
		final int[] integers = new int[] { nSamples, nEvents, dataType,
				nChannels };

		out.writeString(adress);
		out.writeIntArray(integers);
		out.writeFloat(fSample);
		out.writeLong(startTime);
	}

}