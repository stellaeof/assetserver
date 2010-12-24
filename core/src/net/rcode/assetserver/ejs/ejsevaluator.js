function(fragments) {
	return function(writer) {
		//logger.info("Generate template: " + fragments.length);
		var i, fragment, t, value;
		for (i=0; i<fragments.length; i++) {
			fragment=fragments[i];
			t=typeof fragment;
			if (t==='function') {
				value=fragment.call(null);
				//logger.info("fragment interpolation: " + value);
				writer(value);
			} else if (fragment!==null && fragment!==undefined) {
				//logger.info("fragment " + i + "=" + fragment);
				writer(fragment);
			}
		}
	}
}