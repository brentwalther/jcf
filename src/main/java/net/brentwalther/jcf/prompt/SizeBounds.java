package net.brentwalther.jcf.prompt;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class SizeBounds {

  public static SizeBounds create(int maxRows, int maxCols) {
    return new AutoValue_SizeBounds(maxRows, maxCols);
  }

  public abstract int getMaxRows();

  public abstract int getMaxCols();
}
