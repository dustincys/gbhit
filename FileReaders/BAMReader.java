﻿package FileReaders;

import java.io.*;
import java.net.*;
import java.util.*;
import org.w3c.dom.*;

import FileReaders.bam.BAMValueList;
import FileReaders.bam.VariantResolver;
import FileReaders.vcf.Variant;
import net.sf.samtools.*;
import net.sf.samtools.SAMRecord.SAMTagAndValue;
import net.sf.samtools.util.SeekableBufferedStream;
import net.sf.samtools.util.SeekableHTTPStream;
import static FileReaders.XmlWriter.append_text_element;

/**
 * <pre>
 * usage:
 * Firstly, Get an instance of this class:
 *   public BAMReader(String filePath)
 *   if index file exists, use getIndexFilePath() to get .bai filepath through; else, throw FileNotFoundException 
 * Secondly: insert elements into Document Object:
 * 	 readBAM(Document doc, String chr, int start, int end, int windowSize, int step, String mode, String track)
 * 
 * @author Chengwu Yan
 * 
 */
public class BAMReader implements Consts {

	/**
	 * BAM file path or url.
	 */
	private String filePath;
	/**
	 * BAM index file path
	 */
	private String indexFilePath;

	/**
	 * If the BAM file comes from remote server: fromRemoteServer=true, false
	 * else. If fromRemoteServer=false, filePath is a path of a local BAM file,
	 * else an url of a remote BAM file.
	 */
	private boolean fromRemoteServer;

	private SAMFileReader samReader = null;

	private static final int SIXTEENK = 16 * 1024;

	/**
	 * position of each chromosome's first read's first base in the BAM file.
	 * Number of this array's elements is (sequenceNum + 1), the last one is the
	 * BAM file size.
	 */
	private long[] chrFileSize = null;

	/**
	 * last linear bin span of each chromosome
	 */
	private long[] chrLastLinearSpan = null;
	/**
	 * number of reads of the given chromosome in the BAM file
	 */
	private int chrReadNum;

	/**
	 * total number of linear bin of each chromosome in the BAM file
	 */
	private int[] chrLinearBinNum = null;

	/**
	 * number of chromosome in the BAM file
	 */
	private int sequenceNum = 0;

	public BAMReader() {
	}

	/**
	 * 
	 * @param filePath
	 *            file path or url of the BAM file.
	 * @param indexFilePath
	 *            file path of the BAM index file in the server.
	 * @param fromRemoteServer
	 *            If the BAM file comes from remote server,
	 *            fromRemoteServer=true, else false.
	 * @throws MalformedURLException
	 * @throws URISyntaxException
	 */
	public BAMReader(String filePath, String indexFilePath,
			boolean fromRemoteServer) throws MalformedURLException,
			URISyntaxException {
		this.filePath = filePath;
		this.indexFilePath = indexFilePath;
		this.fromRemoteServer = fromRemoteServer;
	}

	/**
	 * if the index file does't exist, throw FileNotFoundException
	 * 
	 * @param filePath
	 *            file path or url of the BAM file.
	 * @throws MalformedURLException
	 * @throws URISyntaxException
	 * @throws FileNotFoundException
	 */
	public BAMReader(String filePath) throws MalformedURLException,
			URISyntaxException, FileNotFoundException {
		this.filePath = filePath;
		if (filePath.startsWith("http://") || filePath.startsWith("ftp://")
				|| filePath.startsWith("https://")) {
			this.fromRemoteServer = true;

			if (isRemoteFileExists(filePath + ".bai"))
				this.indexFilePath = filePath + ".bai";
			else if (BAMReader.isRemoteFileExists(filePath.replace(".bam",
					".bai")))
				this.indexFilePath = filePath.replace(".bam", ".bai");
			else
				throw new FileNotFoundException();
		} else {
			this.fromRemoteServer = false;
			if (new File(filePath + ".bai").exists())
				this.indexFilePath = filePath + ".bai";
			else if (new File(filePath.replace(".bam", ".bai")).exists())
				this.indexFilePath = filePath.replace(".bam", ".bai");
			else
				throw new FileNotFoundException();
		}
	}

	/**
	 * Judge remote file exists
	 */
	public static boolean isRemoteFileExists(String url) {
		URL serverUrl;
		try {
			serverUrl = new URL(url);
			HttpURLConnection urlcon = (HttpURLConnection) serverUrl
					.openConnection();

			String message = urlcon.getHeaderField(0);
			if (message != null && message.startsWith("HTTP/1.1 200 OK")) {
				return true;
			}
			return false;
		} catch (MalformedURLException e) {
			return false;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * get reads overlaps the given region.
	 * 
	 * @param doc
	 *            Document object
	 * @param chr
	 *            chromosome name
	 * @param start
	 *            start base of the given region in the chromosome. 1-base
	 * @param end
	 *            end base of the given region in the chromosome. 1-base
	 * @param step
	 *            A step defines how many pixes show a grid.
	 * @param windowSize
	 *            window size, in pixel. Make sure that size Can be divided by 2
	 * @param track
	 * @param mode
	 *            <pre>
	 * 1:detail
	 * 2:NotDetailSmall
	 * 3:NotDetailBig
	 * @throws IOException
	 */
	public Element readBAM(Document doc, String chr, int start, int end,
			int windowSize, int step, String mode, String track)
			throws SAMFormatException, IOException {
		Element ele = null;
		double bpp = 0;
		boolean already = false;

		bpp = (end - start + 1) / (double) windowSize;

		if (bpp <= 25) {
			List<SAMRecord> list = readSmallRegion(chr, start, end);

			// has more than 20000 reads
			if (list == null) {
				bpp = 1024;
			} else {
				if (mode.equalsIgnoreCase(MODE_DETAIL)) {
					// output all field to the XML file
					ele = writeDetail(doc, list, track);
				} else {
					ele = writeNotDetail(doc, list, track, mode, bpp <= 0.5);
				}
				already = true;
			}
		}
		// middle region
		if (bpp < 8 * 1024 && !already) {
			String result = readMiddleRegion(chr, start, end, windowSize, step);
			ele = writeBigRegion(doc, start, end, step, result, track);
			already = true;
		}
		// big region
		if (bpp >= 8 * 1024 && !already) {
			String result = readBigRegion(chr, start, end, windowSize, step);
			ele = writeBigRegion(doc, start, end, step, result, track);
		}

		return ele;
	}

	private Element writeDetail(Document doc, List<SAMRecord> list, String track) {
		Element reads = doc.createElement(XML_TAG_READS);
		SAMRecord rec = null;
		Element read = null;
		reads.setAttribute(XML_TAG_ID, track);

		Iterator<SAMRecord> itor = list.iterator();
		while (itor.hasNext()) {
			rec = itor.next();
			read = doc.createElement(XML_TAG_READ);
			read.setAttribute(XML_TAG_ID, rec.getReadName());

			int[][] startEnds = getStartEndByCigar(rec);
			append_text_element(doc, read, XML_TAG_FROM,
					BAMValueList.intArray2IntString(startEnds[0], ','));
			append_text_element(doc, read, XML_TAG_TO,
					BAMValueList.intArray2IntString(startEnds[1], ','));
			append_text_element(doc, read, XML_TAG_DIRECTION,
					((rec.getFlags() & 0x10) == 0x10) ? "+" : "-");
			append_text_element(doc, read, "Mapq", rec.getMappingQuality() + "");
			append_text_element(doc, read, "Cigar", rec.getCigarString());
			append_text_element(doc, read, "Rnext", rec.getMateReferenceName());
			append_text_element(doc, read, "Pnext", rec.getMateAlignmentStart()
					+ "");
			append_text_element(doc, read, "Tlen", rec.getInferredInsertSize()
					+ "");
			XmlWriter
					.append_text_element(doc, read, "Seq", rec.getReadString());
			append_text_element(doc, read, "Qual", rec.getBaseQualityString());

			StringBuilder remain = new StringBuilder();
			List<SAMTagAndValue> l = rec.getAttributes();
			for (int i = 0; i < l.size(); i++) {
				remain.append(l.get(i).tag + ":" + l.get(i).value);
				if (i < l.size() - 1)
					remain.append(";");
			}

			append_text_element(doc, read, XML_TAG_DESCRIPTION,
					remain.toString());

			reads.appendChild(read);
		}
		doc.getElementsByTagName(DATA_ROOT).item(0).appendChild(reads);

		return reads;
	}

	/**
	 * 
	 * @param doc
	 * @param list
	 * @param track
	 * @param mode
	 * @param lt0point5
	 *            True if bpp less than 0.5, false else
	 */
	private Element writeNotDetail(Document doc, List<SAMRecord> list,
			String track, String mode, boolean lt0point5) {
		Element reads = doc.createElement(XML_TAG_READS);
		SAMRecord rec = null;
		Element read = null;
		reads.setAttribute(XML_TAG_ID, track);

		Iterator<SAMRecord> itor = list.iterator();
		while (itor.hasNext()) {
			rec = itor.next();
			read = doc.createElement(XML_TAG_READ);

			if (mode.equalsIgnoreCase(MODE_FULL)
					|| mode.equalsIgnoreCase(MODE_PACK))
				read.setAttribute(XML_TAG_ID, rec.getReadName());
			int[][] startEnds = getStartEndByCigar(rec);
			append_text_element(doc, read, XML_TAG_FROM,
					BAMValueList.intArray2IntString(startEnds[0], ','));
			append_text_element(doc, read, XML_TAG_TO,
					BAMValueList.intArray2IntString(startEnds[1], ','));
			append_text_element(doc, read, XML_TAG_DIRECTION,
					((rec.getFlags() & 0x10) == 0x10) ? "+" : "-");
			append_text_element(doc, read, "Mapq", rec.getMappingQuality() + "");

			if (lt0point5) {
				Variant[] vs = new VariantResolver(rec).getVariants();
				for (Variant v : vs) {
					v.write2xml(doc, read);
				}
			}
			reads.appendChild(read);
		}
		doc.getElementsByTagName(DATA_ROOT).item(0).appendChild(reads);
		return reads;
	}

	private int[][] getStartEndByCigar(SAMRecord rec) {
		int pos = rec.getAlignmentStart();
		CigarOperator op = null;
		int[][] startEnds = new int[2][10];
		int len = 10;
		int count = 0;

		startEnds[0][count] = pos;
		for (CigarElement ce : rec.getCigar().getCigarElements()) {
			op = ce.getOperator();
			if (op == CigarOperator.M || op == CigarOperator.D) {
				pos += ce.getLength();
			} else if (op == CigarOperator.N) {
				startEnds[1][count++] = pos - 1;
				if (len == count) {
					startEnds = expandCapacity(len, startEnds);
					len += len;
				}
				pos += ce.getLength();
				startEnds[0][count] = pos;
				continue;
			}
		}
		startEnds[1][count++] = pos - 1;

		int[][] dest = new int[2][count];
		System.arraycopy(startEnds[0], 0, dest[0], 0, count);
		System.arraycopy(startEnds[1], 0, dest[1], 0, count);

		return dest;
	}

	private int[][] expandCapacity(int len, int[][] src) {
		int[][] dest = new int[2][len + len];
		System.arraycopy(src[0], 0, dest[0], 0, len);
		System.arraycopy(src[1], 0, dest[1], 0, len);

		return dest;
	}

	private Element writeBigRegion(Document doc, int start, int end, int step,
			String list, String track) {
		Element values = doc.createElement(XML_TAG_VALUES);
		values.setAttribute(XML_TAG_ID, track);
		values.setAttribute(XML_TAG_TYPE, "REN");

		append_text_element(doc, values, XML_TAG_FROM, start + "");
		append_text_element(doc, values, XML_TAG_TO, end + "");
		append_text_element(doc, values, XML_TAG_STEP, step + "");
		append_text_element(doc, values, "ValueList", list);

		doc.getElementsByTagName(DATA_ROOT).item(0).appendChild(values);
		return values;
	}

	/**
	 * return all reads overlaps in the given region. If reads more than 20000,
	 * return null. Remember that if you want to read all reads in the list,
	 * don't use the method: get(index) because it will reduce the efficiency.
	 * Just get an iterator from the list.
	 * 
	 * @param chr
	 *            name of chromosome
	 * @param start
	 *            start base int the chromosome. 1-base
	 * @param end
	 *            end base int the chromosome. 1-base
	 * @return
	 * @throws MalformedURLException
	 * @throws FileNotFoundException
	 */
	private List<SAMRecord> readSmallRegion(String chr, int start, int end)
			throws MalformedURLException, FileNotFoundException,
			SAMFormatException {
		SAMRecordIterator itor = getIterator(chr, start, end);

		if (itor == null) {
			close();
			return new LinkedList<SAMRecord>();
		}

		List<SAMRecord> list = new LinkedList<SAMRecord>();

		int num = 0;
		SAMRecord rec = null;
		while (itor.hasNext()) {
			rec = itor.next();
			if ("*".equals(rec.getCigarString()))
				continue;
			num++;
			if (num > 20000) {
				list = null;
				break;
			}
			list.add(rec);
		}

		close();

		return list;
	}

	/**
	 * 
	 * @param chr
	 *            name of chromosome
	 * @param start
	 *            start base int the chromosome. 1-base
	 * @param end
	 *            end base int the chromosome. 1-base
	 * @param windowSize
	 *            size of the browser in pixel
	 * @param step
	 *            A step defines how many pixes show a grid.
	 * @return
	 * @throws MalformedURLException
	 * @throws FileNotFoundException
	 */
	private String readMiddleRegion(String chr, int start, int end,
			int windowSize, int step) throws MalformedURLException,
			FileNotFoundException {
		SAMRecordIterator itor = getIterator(chr, start, end);
		BAMValueList list = new BAMValueList(start, end, windowSize, step);
		SAMRecord read = null;
		while (itor.hasNext()) {
			read = itor.next();
			if ("*".equals(read.getCigarString()))
				continue;
			int[][] startEnds = getStartEndByCigar(read);
			for (int i = 0; i < startEnds[0].length; i++) {
				list.update(startEnds[0][i] - start, startEnds[1][i] - start);
			}
		}
		close();

		return BAMValueList.doubleArray2IntString(list.getResults(), ';');
	}

	/**
	 * @param chr
	 *            name of chromosome
	 * @param start
	 *            start base. 1-base
	 * @param end
	 *            end base. 1-base
	 * @param windowSize
	 * @param step
	 *            A step defines how many pixes show a grid.
	 * 
	 * @return
	 */
	private String readBigRegion(String chr, int start, int end,
			int windowSize, int step) throws IOException {
		open(false);
		/*
		 * index of the given chromosome in BAM file order
		 */
		int refIndex = samReader.getFileHeader().getSequenceIndex(chr);
		readReference(refIndex);

		InputStream is1 = getInputStream(indexFilePath);
		readLinearIndex(is1);
		is1.close();

		int startLinear = LinearIndex.convertToLinearIndexOffset(start);
		int endLinear = LinearIndex.convertToLinearIndexOffset(end);

		int nLinearBins = endLinear - startLinear + 1;

		long[] pos = new long[nLinearBins + 1];

		for (int i = 0; i <= nLinearBins; i++)
			pos[i] = 0;

		InputStream is2 = getInputStream(indexFilePath);

		location(refIndex, is2, startLinear);

		for (int i = 0; i < nLinearBins; i++) {
			pos[i] = TabixReader.readLong(is2) >> 16;
		}
		// The last linear index of the given region may be the last linear
		// index of the chromosome,
		// so we must find the first linear index which != 0 from the next
		// chromosomes backward.
		if (endLinear + 1 == chrLinearBinNum[refIndex]) {
			for (int i = refIndex + 1; i <= sequenceNum; i++) {
				if (chrFileSize[i] != 0) {
					pos[nLinearBins] = chrFileSize[i];
					break;
				}
			}
		} else {
			pos[nLinearBins] = TabixReader.readLong(is2) >> 16;
		}
		is2.close();

		int[] regions = new int[nLinearBins];
		for (int i = 0; i < nLinearBins; i++)
			regions[i] = 0;

		// If the first linear index equals 0, we must find the first linear
		// index which != 0 from the previous
		// chromosomes forward.
		if (pos[0] == 0) {
			long l = 0;
			for (int i = refIndex - 1; i >= 0; i++) {
				if (chrLastLinearSpan[i] != 0) {
					l = chrLastLinearSpan[i];
					break;
				}
			}
			for (int i = 0; pos[i] == 0 && i < pos.length - 1; i++)
				pos[i] = l;
		}

		long refSpan = chrFileSize[refIndex + 1] - chrFileSize[refIndex];
		boolean firstZero = true;
		int firstZeroIndex = 0;

		for (int i = 1; i < nLinearBins; i++) {
			if (pos[i - 1] == pos[i]) {
				if (firstZero) {
					firstZeroIndex = i;
					firstZero = false;
				}
				regions[i] = 0;
			} else {
				if (!firstZero) {
					regions[firstZeroIndex - 1] = (int) (((pos[i] - pos[i - 1]) * chrReadNum) / refSpan);
					firstZero = true;
				}
				regions[i] = (int) (((pos[i + 1] - pos[i]) * chrReadNum) / refSpan);
			}
		}

		close();

		return BAMValueList.doubleArray2IntString(getSpan(start % SIXTEENK,
				(end - start + 1) / (windowSize / ((double) step)), windowSize
						/ step, regions), ';');
	}

	/**
	 * 
	 * @param startTh
	 *            startTh = start%16K;
	 * @param width
	 *            width=(end - start + 1)/(windowSize/(double) step);
	 * @param size
	 *            size=windowSize/step;
	 * @param regions
	 * @return
	 */
	private double[] getSpan(int startTh, double width, int size, int[] regions) {
		double[] result = new double[size];
		for (int i = 0; i < size; i++)
			result[i] = 0;

		int bin = 0;
		double curpos = startTh;

		boolean b;
		double endpos;
		double len;
		double binEndPos;
		for (int i = 0; i < size; i++) {
			b = true;
			endpos = startTh + (i + 1) * width - 1;
			while (b) {
				len = 0;
				binEndPos = (bin + 1) * SIXTEENK;
				if (endpos > binEndPos) {
					len = binEndPos - curpos;
					result[i] += (len / SIXTEENK) * regions[bin];
					curpos = binEndPos;
					bin++;
				} else {
					len = endpos - curpos;
					result[i] += (len / SIXTEENK) * regions[bin];
					curpos = endpos;
					b = false;
				}
			}
		}

		return result;
	}

	private void location(int refIndex, InputStream is, int start)
			throws IOException {
		is.skip(8);

		int nBins;
		int nChunks;
		int nLinearBins;
		for (int i = 0; i < refIndex; i++) {
			nBins = TabixReader.readInt(is);
			for (int j = 0; j < nBins; j++) {
				is.skip(4);
				nChunks = TabixReader.readInt(is);
				is.skip(16 * nChunks);
			}
			nLinearBins = TabixReader.readInt(is);
			is.skip(8 * nLinearBins);
		}

		nBins = TabixReader.readInt(is);
		for (int j = 0; j < nBins; j++) {
			is.skip(4);
			nChunks = TabixReader.readInt(is);
			is.skip(16 * nChunks);
		}

		is.skip(4 + 8 * start);
	}

	private void readReference(int refIndex) {
		AbstractBAMFileIndex index = (AbstractBAMFileIndex) samReader
				.getIndex();
		sequenceNum = index.getNumberOfReferences();
		chrLinearBinNum = new int[sequenceNum];
		chrLastLinearSpan = new long[sequenceNum];
		chrFileSize = new long[sequenceNum + 1];

		// read num of given chromosome
		chrReadNum = index.getMetaData(refIndex).getAlignedRecordCount();
	}

	private void readLinearIndex(InputStream is) throws IOException {
		is.skip(8);

		int nBins;
		int nChunks;
		int nLinearBins;
		for (int i = 0; i < sequenceNum; i++) {
			nBins = TabixReader.readInt(is);
			for (int j = 0; j < nBins; j++) {
				is.skip(4);
				nChunks = TabixReader.readInt(is);
				is.skip(16 * nChunks);
			}

			nLinearBins = TabixReader.readInt(is);
			chrLinearBinNum[i] = nLinearBins;

			int k = 0;
			for (k = 1; k <= nLinearBins; k++) {
				chrFileSize[i] = TabixReader.readLong(is) >> 16;
				if (chrFileSize[i] != 0) {
					break;
				}
			}
			if (k < nLinearBins) {
				is.skip(8 * (nLinearBins - k - 1));
				chrLastLinearSpan[i] = TabixReader.readLong(is) >> 16;
			} else {
				chrLastLinearSpan[i] = chrFileSize[i];
			}
		}

		chrFileSize[sequenceNum] = samReader.getIndex()
				.getStartOfLastLinearBin();
	}

	private SAMRecordIterator getIterator(String chr, int start, int end)
			throws MalformedURLException, FileNotFoundException {
		try {
			open(false);

			SAMRecordIterator samRecItor = samReader.queryOverlapping(chr,
					start, end);

			return samRecItor;
		} catch (SAMException e) {
			return null;
		}
	}

	private void open(boolean b) throws MalformedURLException,
			FileNotFoundException {
		if (fromRemoteServer)
			samReader = new SAMFileReader(new SeekableBufferedStream(
					new SeekableHTTPStream(new URL(filePath))),
					new SeekableBufferedStream(new SeekableHTTPStream(new URL(
							indexFilePath))), b);
		else
			samReader = new SAMFileReader(new File(filePath), new File(
					indexFilePath), b);
	}

	private void close() {
		if (samReader != null) {
			samReader.close();
			samReader = null;
		}
	}

	private InputStream getInputStream(String path)
			throws MalformedURLException, FileNotFoundException {
		if (fromRemoteServer)
			return new SeekableBufferedStream(new SeekableHTTPStream(new URL(
					path)));
		else
			return new FileInputStream(new File(path));
	}
}
