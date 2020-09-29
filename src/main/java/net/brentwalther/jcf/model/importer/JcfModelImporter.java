package net.brentwalther.jcf.model.importer;

import net.brentwalther.jcf.model.JcfModel.Model;

public interface JcfModelImporter {
  /** Returns the model parsed by this importer. */
  Model get();
}
